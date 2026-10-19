package net.pieroxy.mom.diagnostics;

import org.xbill.DNS.DClass;
import org.xbill.DNS.Flags;
import org.xbill.DNS.MXRecord;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.Section;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.TLSARecord;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;
import org.xbill.DNS.dnssec.ValidatingResolver;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * {@link DaneDns} doing its own DNSSEC validation (dnsjava's {@link ValidatingResolver}, anchored
 * at the root zone's keys), over the system's resolver — unless that one doesn't pass DNSSEC data
 * along (systemd-resolved with DNSSEC off sometimes strips it), in which case it falls back to
 * Cloudflare's public resolver. Only this check's lookups go there: the account's MX host names.
 */
public class DnsJavaDaneDns implements DaneDns {
  private static final Logger LOGGER = Logger.getLogger(DnsJavaDaneDns.class.getName());
  static final String FALLBACK_RESOLVER = "1.1.1.1";
  private static final Duration TIMEOUT = Duration.ofSeconds(5);
  // The root zone's key-signing keys, KSK-2017 and KSK-2024 (https://data.iana.org/root-anchors/).
  private static final String ROOT_TRUST_ANCHORS =
      ". IN DS 20326 8 2 E06D44B80B8F1D39A95C0B0D7C65D08458E880409BBC683457104237C7F8EC8D\n"
          + ". IN DS 38696 8 2 683D2D0ACB8C9B712A1948B27F741219298D0A450D612C483AF444A4C0FB2B16\n";

  private final ValidatingResolver resolver;
  private final String description;

  public DnsJavaDaneDns() {
    try {
      ValidatingResolver system = validating(new SimpleResolver());
      if (passesDnssec(system)) {
        resolver = system;
        description = "the system's DNS resolver";
      } else {
        LOGGER.info("The system's DNS resolver doesn't pass DNSSEC data: DANE lookups go through " + FALLBACK_RESOLVER);
        resolver = validating(new SimpleResolver(FALLBACK_RESOLVER));
        description = FALLBACK_RESOLVER + " (Cloudflare): the system's DNS resolver doesn't pass the DNSSEC data DANE needs";
      }
    } catch (IOException e) {
      throw new IllegalStateException("Could not initialize the DNS resolver", e);
    }
  }

  private static ValidatingResolver validating(SimpleResolver head) throws IOException {
    head.setTimeout(TIMEOUT);
    ValidatingResolver v = new ValidatingResolver(head);
    v.loadTrustAnchors(new ByteArrayInputStream(ROOT_TRUST_ANCHORS.getBytes(StandardCharsets.US_ASCII)));
    v.setTimeout(TIMEOUT);
    return v;
  }

  /** The root zone is signed: if its answer can't be validated, the upstream resolver strips DNSSEC data. */
  private static boolean passesDnssec(ValidatingResolver resolver) {
    try {
      Message response = resolver.send(Message.newQuery(Record.newRecord(Name.root, Type.SOA, DClass.IN)));
      return response.getRcode() == Rcode.NOERROR && response.getHeader().getFlag(Flags.AD);
    } catch (IOException e) {
      return false;
    }
  }

  @Override
  public String describe() {
    return description;
  }

  @Override
  public Answer<String> mx(String domain) throws DnsFailure {
    return query(domain, Type.MX, r -> r instanceof MXRecord mx ? strip(mx.getTarget().toString()) : null);
  }

  @Override
  public Answer<Tlsa> tlsa(String name) throws DnsFailure {
    return query(name, Type.TLSA, r -> r instanceof TLSARecord t
        ? new Tlsa(t.getCertificateUsage(), t.getSelector(), t.getMatchingType(), t.getCertificateAssociationData()) : null);
  }

  private <T> Answer<T> query(String name, int type, Function<Record, T> convert) throws DnsFailure {
    Message response;
    try {
      response = resolver.send(Message.newQuery(Record.newRecord(Name.fromString(name.endsWith(".") ? name : name + "."), type, DClass.IN)));
    } catch (TextParseException e) {
      throw new DnsFailure("Invalid name: " + name);
    } catch (IOException e) {
      throw new DnsFailure("DNS lookup of " + Type.string(type) + " " + name + " failed: " + e.getMessage());
    }
    String reason = null;
    for (Record r : response.getSection(Section.ADDITIONAL)) {
      if (r.getDClass() == ValidatingResolver.VALIDATION_REASON_QCLASS) reason = r.rdataToString().replaceAll("^\"|\"$", "");
    }
    int rcode = response.getRcode();
    if (rcode == Rcode.SERVFAIL && reason != null) return new Answer<>(List.of(), Security.BOGUS, reason);
    if (rcode != Rcode.NOERROR && rcode != Rcode.NXDOMAIN) {
      throw new DnsFailure("DNS lookup of " + Type.string(type) + " " + name + " failed: " + Rcode.string(rcode));
    }
    List<T> records = new ArrayList<>();
    for (Record r : response.getSection(Section.ANSWER)) {
      T converted = convert.apply(r);
      if (converted != null) records.add(converted);
    }
    return new Answer<>(records, response.getHeader().getFlag(Flags.AD) ? Security.SECURE : Security.INSECURE, reason);
  }

  private static String strip(String name) {
    return name.endsWith(".") ? name.substring(0, name.length() - 1) : name;
  }
}
