package net.pieroxy.mom.diagnostics;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Checks DANE for SMTP (RFC 7672) on a domain's mail servers: TLSA records at
 * {@code _25._tcp.<MX host>}, published in DNSSEC-signed zones, pin the certificate each MX
 * presents, so that sending servers validating DANE can't be tricked into talking to an impostor.
 * Optional hardening: no TLSA record at all is only information. But records that are there must
 * be right — a validating sender refuses to deliver to an MX whose certificate matches none of
 * its usable records, or whose DNSSEC is broken — so each MX that has some is connected to on
 * port 25 to check its certificate against them. When that's impossible from where MOM runs (port
 * 25 is often blocked on home connections), the check is partial.
 */
public class DaneCheck {
  /** MX hosts with TLSA records are probed in parallel, up to this many. */
  static final int MAX_PROBED_HOSTS = 10;
  private final DaneDns dns;
  private final StartTlsProbe.CertificateSource certificates;

  public DaneCheck(DaneDns dns, StartTlsProbe.CertificateSource certificates) {
    this.dns = dns;
    this.certificates = certificates;
  }

  public DeliveryCheckResultDto run(String domain) {
    DeliveryCheckResultDto result = new DeliveryCheckResultDto(DeliveryCheck.DANE).fact("Domain", domain);
    result.fact("DNS resolver", dns.describe());

    DaneDns.Answer<String> mx;
    try {
      mx = dns.mx(domain);
    } catch (DaneDns.DnsFailure e) {
      return result.finding(CheckStatus.WARNING, "Could not query the DNS for " + domain + "'s MX records (" + e.getMessage()
          + "). Try again later.").conclude("");
    }
    if (mx.security() == DaneDns.Security.BOGUS) {
      return result.finding(CheckStatus.ERROR, domain + "'s DNSSEC is broken (" + mx.reason() + "): resolvers that validate "
          + "DNSSEC refuse its records, so many servers can't deliver mail to it at all. Fix its DNSSEC signatures (or the DS "
          + "record at the registrar).").conclude("");
    }
    if (mx.records().isEmpty()) {
      return result.finding(CheckStatus.INFO, domain + " has no MX record: DANE doesn't apply (see the MX check).").conclude("");
    }
    result.fact("DNSSEC", domain + (mx.security() == DaneDns.Security.SECURE ? " is signed and validates" : " isn't signed"));

    List<String> withTlsa = new ArrayList<>();
    List<String> withoutTlsa = new ArrayList<>();
    List<HostTlsa> usable = new ArrayList<>();
    for (String host : mx.records()) {
      String name = "_25._tcp." + host;
      DaneDns.Answer<DaneDns.Tlsa> tlsa;
      try {
        tlsa = dns.tlsa(name);
      } catch (DaneDns.DnsFailure e) {
        result.finding(CheckStatus.WARNING, "Could not look up " + name + " (" + e.getMessage() + "). Try again later.");
        continue;
      }
      if (tlsa.security() == DaneDns.Security.BOGUS) {
        result.finding(CheckStatus.ERROR, "The DNSSEC of " + host + " is broken (" + tlsa.reason() + "): senders validating DANE "
            + "can't tell whether it has TLSA records, and refuse to deliver to it. Fix its zone's DNSSEC.");
        withTlsa.add(host);
        continue;
      }
      if (tlsa.records().isEmpty()) {
        withoutTlsa.add(host);
        continue;
      }
      withTlsa.add(host);
      result.fact("TLSA " + name, tlsa.records().stream().map(DaneCheck::format).collect(Collectors.joining(", ")));
      if (tlsa.security() != DaneDns.Security.SECURE || mx.security() != DaneDns.Security.SECURE) {
        result.finding(CheckStatus.WARNING, host + " has TLSA records, but " + (mx.security() != DaneDns.Security.SECURE
            ? domain + "'s MX records aren't DNSSEC-signed" : "they aren't DNSSEC-signed") + ": senders only use TLSA records "
            + "reached through DNSSEC from start to finish, so these are ignored. Sign " + (mx.security() != DaneDns.Security.SECURE
            ? domain : host + "'s zone") + " with DNSSEC, or remove them.");
        continue;
      }
      List<DaneDns.Tlsa> good = checkRecords(result, host, tlsa.records());
      if (!good.isEmpty()) {
        result.finding(CheckStatus.OK, host + " has DNSSEC-signed TLSA records that senders can use.");
        usable.add(new HostTlsa(host, good));
      }
    }

    if (withTlsa.isEmpty()) {
      result.finding(CheckStatus.INFO, "DANE isn't set up for " + domain + "'s mail servers. It's optional: it pins your mail "
          + "servers' certificates so senders can't be fooled into talking to an impostor, "
          + (mx.security() == DaneDns.Security.SECURE ? "and " + domain + " is already DNSSEC-signed, its prerequisite: you could "
          + "publish TLSA records at _25._tcp.<MX host>." : "but it requires DNSSEC first, and " + domain + " isn't signed."));
      return result.conclude("DANE isn't set up for " + domain + " (optional).");
    }
    if (!withoutTlsa.isEmpty()) {
      result.finding(CheckStatus.WARNING, "Only some of " + domain + "'s MX hosts have TLSA records (none for "
          + String.join(", ", withoutTlsa) + "): an attacker able to divert traffic can simply steer senders toward those. "
          + "Publish TLSA records for every MX host.");
    }

    List<HostTlsa> probed = usable.subList(0, Math.min(usable.size(), MAX_PROBED_HOSTS));
    List<StartTlsProbe.Result> probes = StartTlsProbe.fetchAll(probed.stream().map(HostTlsa::host).toList(), certificates);
    for (int i = 0; i < probed.size(); i++) {
      report(result, probed.get(i), probes.get(i));
    }
    for (HostTlsa skipped : usable.subList(probed.size(), usable.size())) {
      result.finding(CheckStatus.INFO, "Certificate of " + skipped.host + " not checked: only " + MAX_PROBED_HOSTS + " MX hosts are.");
      result.setPartial(true);
    }
    return result.conclude(result.isPartial()
        ? "DANE is set up for " + domain + " (certificates not checked against the TLSA records: see below)."
        : "DANE is set up for " + domain + ", and its mail servers' certificates match their TLSA records.");
  }

  private record HostTlsa(String host, List<DaneDns.Tlsa> records) {}

  /** @return the records a DANE-validating SMTP client would use. */
  private static List<DaneDns.Tlsa> checkRecords(DeliveryCheckResultDto result, String host, List<DaneDns.Tlsa> records) {
    List<DaneDns.Tlsa> good = new ArrayList<>();
    for (DaneDns.Tlsa t : records) {
      if (t.usage() > 3 || t.selector() > 1 || t.matchingType() > 2) {
        result.finding(CheckStatus.ERROR, "The TLSA record \"" + format(t) + "\" of " + host + " has an unknown usage, selector "
            + "or matching type: senders ignore it.");
      } else if (t.usage() < 2) {
        result.finding(CheckStatus.WARNING, "The TLSA record \"" + format(t) + "\" of " + host + " uses usage " + t.usage()
            + " (PKIX-" + (t.usage() == 0 ? "TA" : "EE") + "), which SMTP senders ignore (RFC 7672 §3.1.3). Use 3 (DANE-EE) "
            + "or 2 (DANE-TA).");
      } else {
        good.add(t);
      }
    }
    if (good.isEmpty() && !records.isEmpty()) {
      result.finding(CheckStatus.ERROR, "None of " + host + "'s TLSA records is usable by SMTP senders: validating ones treat "
          + "it as if its certificate couldn't be verified, and refuse to deliver. Publish a usage 3 (DANE-EE) record.");
    }
    return good;
  }

  private static void report(DeliveryCheckResultDto result, HostTlsa hostTlsa, StartTlsProbe.Result probe) {
    String host = hostTlsa.host;
    if (probe.failure() instanceof StartTlsProbe.NoStartTlsException) {
      result.finding(CheckStatus.ERROR, host + " has TLSA records but doesn't offer STARTTLS: senders validating DANE refuse to "
          + "deliver to it unencrypted. Enable STARTTLS on it.");
      return;
    }
    if (probe.failure() != null) {
      result.setPartial(true);
      result.finding(CheckStatus.INFO, "Couldn't connect to " + host + " on port 25 from here (" + probe.failure().getMessage()
          + "), so its certificate wasn't checked against its TLSA records. Outgoing port 25 is often blocked on home connections.");
      return;
    }
    List<X509Certificate> chain = probe.chain();
    if (chain.isEmpty()) {
      result.finding(CheckStatus.ERROR, host + " presented no certificate.");
      return;
    }
    result.fact("Certificate of " + host, commonName(chain.get(0)) + ", expires "
        + chain.get(0).getNotAfter().toInstant().toString().substring(0, 10));
    for (DaneDns.Tlsa t : hostTlsa.records) {
      if (matches(t, chain)) {
        result.finding(CheckStatus.OK, "The certificate " + host + " presents matches its TLSA record \"" + format(t) + "\".");
        return;
      }
    }
    result.finding(CheckStatus.ERROR, "The certificate " + host + " presents matches none of its TLSA records: senders "
        + "validating DANE refuse to deliver to it. Typically the certificate was renewed with a new key and the TLSA records "
        + "weren't updated: publish the new key's record (ideally before the next renewal, alongside the old one).");
  }

  /** RFC 6698 §2.1: DANE-EE (3) against the server's own certificate, DANE-TA (2) against the rest of its chain. */
  static boolean matches(DaneDns.Tlsa tlsa, List<X509Certificate> chain) {
    List<X509Certificate> candidates = tlsa.usage() == 3 ? chain.subList(0, 1) : chain.subList(Math.min(1, chain.size()), chain.size());
    for (X509Certificate cert : candidates) {
      try {
        byte[] selected = tlsa.selector() == 0 ? cert.getEncoded() : cert.getPublicKey().getEncoded();
        byte[] digest = switch (tlsa.matchingType()) {
          case 0 -> selected;
          case 1 -> MessageDigest.getInstance("SHA-256").digest(selected);
          default -> MessageDigest.getInstance("SHA-512").digest(selected);
        };
        if (Arrays.equals(digest, tlsa.data())) return true;
      } catch (CertificateEncodingException | NoSuchAlgorithmException e) {
        // Can't compute: doesn't match.
      }
    }
    return false;
  }

  /** The certificate's CN, or its whole subject if it has none. */
  private static String commonName(X509Certificate cert) {
    String subject = cert.getSubjectX500Principal().getName();
    try {
      for (Rdn rdn : new LdapName(subject).getRdns()) {
        if (rdn.getType().equalsIgnoreCase("CN")) return rdn.getValue().toString();
      }
    } catch (InvalidNameException e) {
      // Fall back to the whole subject.
    }
    return subject;
  }

  private static String format(DaneDns.Tlsa t) {
    String hex = HexFormat.of().formatHex(t.data());
    return t.usage() + " " + t.selector() + " " + t.matchingType() + " " + (hex.length() > 16 ? hex.substring(0, 16) + "…" : hex);
  }
}
