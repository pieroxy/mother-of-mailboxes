package net.pieroxy.mom.diagnostics;

import net.pieroxy.mom.detection.dkim.DkimVerifier;
import net.pieroxy.mom.detection.dmarc.DmarcDnsException;
import net.pieroxy.mom.detection.dmarc.DmarcDnsResolver;
import net.pieroxy.mom.detection.dmarc.DmarcEvaluator;
import net.pieroxy.mom.detection.spf.SpfDnsResolver;
import net.pieroxy.mom.detection.spf.SpfEvaluator;
import net.pieroxy.mom.detection.spf.SpfResult;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Checks a sending domain's DMARC setup (RFC 7489): first its record in the DNS — found (at the
 * domain itself, or inherited from its organizational domain), valid, and how protective its
 * policy is — then, with a test message (see {@link TestSends}), whether that message actually
 * passes DMARC: an SPF pass or a valid DKIM signature for a domain aligned with the From domain.
 * Without a test message, the check is partial: it only covers the record.
 */
public class DmarcCheck {
  private static final Logger QUIET = Logger.getLogger(DmarcCheck.class.getName());

  private final DmarcDnsResolver dmarcDns;
  private final SpfDnsResolver spfDns;
  private final DkimVerifier dkimVerifier;

  public DmarcCheck(DmarcDnsResolver dmarcDns, SpfDnsResolver spfDns, DkimVerifier dkimVerifier) {
    this.dmarcDns = dmarcDns;
    this.spfDns = spfDns;
    this.dkimVerifier = dkimVerifier;
  }

  /** A record as found in the DNS, and what it means for {@code fromDomain}. */
  private record Found(String publishedAt, String text, Map<String, String> tags, boolean inherited) {}

  public DeliveryCheckResultDto run(String fromDomain, Supplier<TestSendOutcome> testSend) {
    TestSendOutcome outcome = testSend.get();
    if (outcome.kind() == TestSendOutcome.Kind.PENDING) return DeliveryCheckResultDto.inProgress(DeliveryCheck.DMARC, outcome.message());

    DeliveryCheckResultDto result = new DeliveryCheckResultDto(DeliveryCheck.DMARC).fact("Domain", fromDomain);
    Found found;
    try {
      found = find(fromDomain, result);
    } catch (DmarcDnsException e) {
      return result.finding(CheckStatus.WARNING, "Could not query the DNS for " + fromDomain + "'s DMARC record ("
          + e.getMessage() + "). Try again later.").conclude("");
    }
    if (found == null) {
      if (result.getFindings().isEmpty()) {
        result.finding(CheckStatus.WARNING, "No DMARC record for " + fromDomain + ": nothing tells receiving servers what to do "
            + "with mail that fails SPF and DKIM — such as mail forged in your name — and some of them (Gmail, Yahoo…) are "
            + "stricter with domains without one. Start with a TXT record at _dmarc." + fromDomain + " like "
            + "\"v=DMARC1; p=none; rua=mailto:dmarc@" + fromDomain + "\", then tighten the policy once reports show your own "
            + "mail passes.");
      }
      return result.conclude("");
    }

    String policy = addRecordFindings(result, found);
    if (policy == null) return result.conclude(""); // Invalid record: receivers ignore it, nothing more to check.

    switch (outcome.kind()) {
      case RECEIVED -> {
        boolean passes = addTestMessageFindings(result, fromDomain, found, policy, outcome.received());
        return result.conclude("DMARC is set up for " + fromDomain + " (p=" + policy + ")" + (passes ? ", and the test message passes it." : "."));
      }
      case FAILED -> result.finding(CheckStatus.WARNING, "Test send failed: " + outcome.message());
      default -> {
        // No sink: the record is all there is to check.
      }
    }
    result.setPartial(true);
    return result.conclude("DMARC record is set up for " + fromDomain + " (p=" + policy + "; DNS only: no test message to check it against).");
  }

  /** The record that applies to {@code fromDomain}: its own, or its organizational domain's. Null if none (or several). */
  private Found find(String fromDomain, DeliveryCheckResultDto result) throws DmarcDnsException {
    Found own = lookup(fromDomain, false, result);
    if (own != null || !result.getFindings().isEmpty()) return own;
    Optional<String> org = DmarcEvaluator.organizationalDomainOf(fromDomain);
    if (org.isPresent() && !org.get().equalsIgnoreCase(fromDomain)) return lookup(org.get(), true, result);
    return null;
  }

  private Found lookup(String domain, boolean inherited, DeliveryCheckResultDto result) throws DmarcDnsException {
    String name = "_dmarc." + domain;
    List<String> records = dmarcDns.lookupTxt(name).stream()
        .filter(txt -> txt.regionMatches(true, 0, "v=DMARC1", 0, 8)
            && (txt.length() == 8 || txt.charAt(8) == ';' || Character.isWhitespace(txt.charAt(8))))
        .toList();
    if (records.isEmpty()) return null;
    if (records.size() > 1) {
      records.forEach(r -> result.fact("Record", r));
      result.finding(CheckStatus.ERROR, records.size() + " DMARC records at " + name + ": there must be exactly one, receiving "
          + "servers ignore them all. Merge them into one.");
      return null;
    }
    Map<String, String> tags = new LinkedHashMap<>();
    for (String part : records.get(0).split(";")) {
      int eq = part.indexOf('=');
      if (eq > 0) tags.put(part.substring(0, eq).trim().toLowerCase(Locale.ROOT), part.substring(eq + 1).trim());
    }
    return new Found(name, records.get(0), tags, inherited);
  }

  /** @return the policy that applies to the From domain, or null if the record is invalid. */
  private static String addRecordFindings(DeliveryCheckResultDto result, Found found) {
    result.fact("Record", found.text);
    result.fact("Published at", found.publishedAt + (found.inherited ? " (organizational domain: no record of its own)" : ""));

    String p = found.tags.get("p");
    if (p == null) {
      result.finding(CheckStatus.ERROR, "The record has no p= tag: receiving servers ignore it. Add p=none, p=quarantine or p=reject.");
      return null;
    }
    if (!isPolicy(p)) {
      result.finding(CheckStatus.ERROR, "The record's p=" + p + " is invalid: receiving servers ignore it. Use none, quarantine or reject.");
      return null;
    }
    String sp = found.tags.get("sp");
    if (sp != null && !isPolicy(sp)) {
      result.finding(CheckStatus.ERROR, "The record's sp=" + sp + " is invalid: receiving servers ignore the record. Use none, "
          + "quarantine or reject.");
      return null;
    }
    // RFC 7489 §6.3: an inherited record governs subdomains through sp=, falling back to p=.
    String policy = (found.inherited && sp != null ? sp : p).toLowerCase(Locale.ROOT);
    result.finding(CheckStatus.OK, "Valid DMARC record found.");
    switch (policy) {
      case "reject" -> result.finding(CheckStatus.OK, "Policy p=reject: mail failing DMARC — such as mail forged in your name — "
          + "is rejected. The strongest protection.");
      case "quarantine" -> result.finding(CheckStatus.OK, "Policy p=quarantine: mail failing DMARC goes to spam. Move to p=reject "
          + "once you're confident all your own mail passes.");
      default -> result.finding(CheckStatus.WARNING, "Policy p=none: monitoring only — mail failing DMARC, including mail forged "
          + "in your name, is delivered as usual. Once reports show your own mail passes, move to p=quarantine, then p=reject.");
    }

    String pct = found.tags.get("pct");
    if (pct != null && !pct.equals("100") && !policy.equals("none")) {
      result.finding(CheckStatus.WARNING, "pct=" + pct + ": the policy only applies to " + pct + "% of failing mail, the rest "
          + "being treated as with p=none. Remove pct (or set it to 100) once you're confident.");
    }
    String rua = found.tags.get("rua");
    if (rua == null || rua.isBlank()) {
      result.finding(CheckStatus.INFO, "No rua= tag: you get no aggregate reports, so you can't see who sends mail as your domain, "
          + "nor whether your own mail passes. Add rua=mailto:<an address of yours>.");
    } else {
      result.fact("Aggregate reports (rua)", rua);
    }
    if (found.tags.containsKey("ruf")) result.fact("Failure reports (ruf)", found.tags.get("ruf"));
    result.fact("Alignment", "DKIM " + mode(found.tags.get("adkim")) + ", SPF " + mode(found.tags.get("aspf")));
    return policy;
  }

  /** @return whether the test message passes DMARC. */
  private boolean addTestMessageFindings(DeliveryCheckResultDto result, String fromDomain, Found found, String policy,
                                         ReceivedTestMessage received) {
    boolean strictSpf = "s".equalsIgnoreCase(found.tags.get("aspf"));
    boolean strictDkim = "s".equalsIgnoreCase(found.tags.get("adkim"));

    String spfDomain = received.envelopeDomain() != null ? received.envelopeDomain() : received.helo().toLowerCase(Locale.ROOT);
    SpfResult spf = new SpfEvaluator(spfDns).evaluate(received.clientIp(), spfDomain, QUIET);
    boolean spfAligned = spf == SpfResult.PASS && aligned(fromDomain, spfDomain, strictSpf);
    result.fact("SPF of the test message", spf.getCode() + " for " + spfDomain
        + (spf == SpfResult.PASS ? (spfAligned ? " (aligned)" : " (not aligned with " + fromDomain + ")") : ""));

    List<String> dkimDomains = dkimVerifier.verifyDetailed(new ByteArrayInputStream(received.raw()), QUIET).passingDomains();
    Optional<String> dkimAligned = dkimDomains.stream().filter(d -> aligned(fromDomain, d, strictDkim)).findFirst();
    result.fact("DKIM of the test message", dkimDomains.isEmpty() ? "no valid signature"
        : "valid signature by " + String.join(", ", dkimDomains) + (dkimAligned.isPresent() ? " (aligned)" : " (not aligned with " + fromDomain + ")"));

    if (spfAligned || dkimAligned.isPresent()) {
      String via = spfAligned && dkimAligned.isPresent() ? "SPF and DKIM" : spfAligned ? "SPF" : "DKIM";
      result.finding(CheckStatus.OK, "The test message passes DMARC, through " + via + ".");
      if (dkimAligned.isEmpty()) {
        result.finding(policy.equals("none") ? CheckStatus.INFO : CheckStatus.WARNING, "It only passes through SPF: a forwarded "
            + "message is sent on by another server, fails SPF, and then DMARC — and with p=" + policy + ", that one "
            + (policy.equals("reject") ? "is rejected" : policy.equals("quarantine") ? "goes to spam" : "is still delivered")
            + ". A DKIM signature by " + fromDomain + " survives forwarding: see the DKIM check.");
      }
      return true;
    }
    String why = "neither SPF nor DKIM passed for a domain aligned with " + fromDomain + " (see the SPF and DKIM checks)";
    switch (policy) {
      case "reject" -> result.finding(CheckStatus.ERROR, "The test message fails DMARC — " + why + " — and with p=reject, "
          + "receiving servers reject your mail.");
      case "quarantine" -> result.finding(CheckStatus.ERROR, "The test message fails DMARC — " + why + " — and with "
          + "p=quarantine, your mail goes to spam.");
      default -> result.finding(CheckStatus.WARNING, "The test message fails DMARC — " + why + ". Nothing happens with p=none, "
          + "but it will be rejected or sent to spam as soon as you tighten the policy, and some receivers already distrust it.");
    }
    return false;
  }

  private static boolean isPolicy(String value) {
    String v = value.toLowerCase(Locale.ROOT);
    return v.equals("none") || v.equals("quarantine") || v.equals("reject");
  }

  private static String mode(String value) {
    return "s".equalsIgnoreCase(value) ? "strict" : "relaxed";
  }

  private static boolean aligned(String fromDomain, String other, boolean strict) {
    if (strict) return fromDomain.equalsIgnoreCase(other);
    return organizationalDomain(fromDomain).equals(organizationalDomain(other));
  }

  private static String organizationalDomain(String domain) {
    return DmarcEvaluator.organizationalDomainOf(domain).orElse(domain.toLowerCase(Locale.ROOT));
  }
}
