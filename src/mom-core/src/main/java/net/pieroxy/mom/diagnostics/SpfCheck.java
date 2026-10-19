package net.pieroxy.mom.diagnostics;

import net.pieroxy.mom.detection.spf.SpfDnsException;
import net.pieroxy.mom.detection.spf.SpfDnsResolver;
import net.pieroxy.mom.detection.spf.SpfEvaluator;
import net.pieroxy.mom.detection.spf.SpfResult;
import net.pieroxy.mom.detection.dmarc.DmarcEvaluator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks a sending domain's SPF record (RFC 7208): first from DNS alone — that there's exactly
 * one, that it parses, stays within the 10 DNS lookups limit (counted through every include and
 * redirect), avoids {@code ptr}, and ends with a protective {@code all}. Then against a test send
 * (see {@link TestSends}), if an SMTP sink is configured: the real SPF result of a message sent
 * through the account, from the server that actually delivered it and for its actual envelope
 * sender. Without one (no sink configured, or the test send failed) the check is partial, and only
 * offers a guess as information: whether the account's SMTP server is one of the servers the
 * record authorizes — a guess, since the server mail is submitted to isn't always the one that
 * delivers it (big providers send from other servers).
 */
public class SpfCheck {
  // RFC 7208 §4.6.4.
  private static final int MAX_DNS_LOOKUPS = 10;
  private static final int MAX_DEPTH = 10;
  // Same grammar as SpfEvaluator's: a qualifier, a mechanism name, an optional ":value" and CIDR lengths.
  private static final Pattern MECHANISM = Pattern.compile(
      "^([+\\-~?])?(all|include|a|mx|ptr|ip4|ip6|exists)(?::([^/]+))?(?:/(\\d+))?(?:/(\\d+))?$",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern MODIFIER = Pattern.compile("^([a-zA-Z][a-zA-Z0-9_.-]*)=(.*)$");

  private final SpfDnsResolver resolver;

  public SpfCheck(SpfDnsResolver resolver) {
    this.resolver = resolver;
  }

  /**
   * @param smtpHost the account's SMTP server, or null if it has none.
   * @param testSend the test send's outcome, asked first: a pending one makes the whole check
   *     pending (see {@link DeliveryCheckResultDto#inProgress}).
   */
  public DeliveryCheckResultDto run(String domain, String smtpHost, Supplier<TestSendOutcome> testSend) {
    TestSendOutcome outcome = testSend.get();
    if (outcome.kind() == TestSendOutcome.Kind.PENDING) return DeliveryCheckResultDto.inProgress(DeliveryCheck.SPF, outcome.message());

    DeliveryCheckResultDto result = new DeliveryCheckResultDto(DeliveryCheck.SPF).fact("Domain", domain);

    List<String> records;
    try {
      records = spfRecords(domain);
    } catch (SpfDnsException e) {
      return result.finding(CheckStatus.WARNING, "Could not query the DNS for " + domain + "'s records (" + e.getMessage()
          + "). Try again later.").conclude("");
    }
    if (records.isEmpty()) {
      return result.finding(CheckStatus.ERROR, "No SPF record found for " + domain + ": receiving servers can't tell which "
          + "servers may send mail for it. Add a TXT record starting with \"v=spf1\".").conclude("");
    }
    if (records.size() > 1) {
      records.forEach(r -> result.fact("Record", r));
      return result.finding(CheckStatus.ERROR, records.size() + " SPF records found for " + domain + ": there must be exactly "
          + "one, receiving servers treat this as a permanent error (PermError). Merge them into one.").conclude("");
    }
    String record = records.get(0);
    result.fact("Record", record);
    result.finding(CheckStatus.OK, "Exactly one SPF record found.");

    Analysis analysis = new Analysis();
    Character all = analyze(domain, record, 0, new HashSet<>(), analysis);
    analysis.findings.forEach(f -> result.finding(f.getStatus(), f.getMessage()));

    result.fact("DNS lookups", analysis.lookups + " (limit: " + MAX_DNS_LOOKUPS + ")");
    if (analysis.lookups > MAX_DNS_LOOKUPS) {
      result.finding(CheckStatus.ERROR, lookups(analysis.lookups) + " needed to evaluate the record, over the limit of "
          + MAX_DNS_LOOKUPS + ": receiving servers treat it as a permanent error (PermError). Replace some include/a/mx "
          + "with ip4/ip6 ranges, or remove unused includes.");
    } else if (analysis.lookups >= MAX_DNS_LOOKUPS - 1) {
      result.finding(CheckStatus.WARNING, lookups(analysis.lookups) + " needed to evaluate the record, close to the limit of "
          + MAX_DNS_LOOKUPS + ": one more include could break it.");
    } else {
      result.finding(CheckStatus.OK, lookups(analysis.lookups) + " needed to evaluate the record, within the limit of "
          + MAX_DNS_LOOKUPS + ".");
    }

    if (analysis.ptrUsed) {
      result.finding(CheckStatus.WARNING, "The record uses the \"ptr\" mechanism, deprecated by RFC 7208: slow, unreliable, "
          + "and ignored by many receivers. Use ip4, ip6 or a instead.");
    }

    addAllFinding(result, domain, all);
    if (outcome.kind() == TestSendOutcome.Kind.RECEIVED) {
      addTestSendFindings(result, domain, outcome.received());
      return result.conclude("SPF is correctly set up for " + domain + ".");
    }
    if (outcome.kind() == TestSendOutcome.Kind.FAILED) result.finding(CheckStatus.WARNING, "Test send failed: " + outcome.message());
    addSmtpHostGuess(result, domain, smtpHost);
    result.setPartial(true);
    return result.conclude("SPF record is correctly set up for " + domain + " (DNS only: no test message to check it against).");
  }

  private static String lookups(int n) {
    return n + (n == 1 ? " DNS lookup" : " DNS lookups");
  }

  private static void addAllFinding(DeliveryCheckResultDto result, String domain, Character all) {
    if (all == null) {
      result.finding(CheckStatus.WARNING, "The record doesn't end with \"all\": servers it doesn't list get a neutral result, "
          + "which gives no protection against someone sending mail as " + domain + ". End it with -all or ~all.");
      return;
    }
    switch (all) {
      case '-' -> result.finding(CheckStatus.OK, "The record ends with -all: mail from servers it doesn't list fails SPF.");
      case '~' -> result.finding(CheckStatus.OK, "The record ends with ~all (soft fail): mail from servers it doesn't list is "
          + "accepted but marked suspicious. -all is stricter, once you're sure every server sending your mail is listed.");
      case '?' -> result.finding(CheckStatus.WARNING, "The record ends with ?all (neutral): it says nothing about servers it "
          + "doesn't list, which gives no protection against someone sending mail as " + domain + ". Use -all or ~all.");
      default -> result.finding(CheckStatus.ERROR, "The record ends with +all: any server in the world may send mail as "
          + domain + ". Use -all or ~all.");
    }
  }

  /** Without a test message: whether the account's SMTP server is allowed by the record — information only, never a verdict. */
  private void addSmtpHostGuess(DeliveryCheckResultDto result, String domain, String smtpHost) {
    if (smtpHost == null || smtpHost.isBlank()) return;
    List<String> ips = new ArrayList<>();
    try {
      ips.addAll(resolver.lookupA(smtpHost));
      ips.addAll(resolver.lookupAaaa(smtpHost));
    } catch (SpfDnsException e) {
      return;
    }
    if (ips.isEmpty()) return;
    result.fact("SMTP server", smtpHost + " (" + String.join(", ", ips) + ")");
    SpfEvaluator evaluator = new SpfEvaluator(resolver);
    for (String ip : ips) {
      SpfResult spf = evaluator.evaluate(ip, domain);
      String server = smtpHost + " (" + ip + ")";
      switch (spf) {
        case PASS -> result.finding(CheckStatus.INFO, "Your SMTP server " + server + " is allowed by the record. That's a good "
            + "sign, but it may not be the server that actually delivers your mail.");
        case FAIL, SOFTFAIL, NEUTRAL -> result.finding(CheckStatus.INFO, "Your SMTP server " + server + " isn't allowed by the "
            + "record (SPF result: " + spf.getCode() + "). That's fine if your provider delivers your mail from other servers "
            + "than the one you submit it to, as big providers do.");
        default -> {
          // PERMERROR: already reported from the record's own analysis. TEMPERROR: nothing to guess from.
        }
      }
    }
  }

  /** The real SPF result of the test message, and whether it can count for {@code fromDomain}'s DMARC. */
  private void addTestSendFindings(DeliveryCheckResultDto result, String fromDomain, ReceivedTestMessage received) {
    // RFC 7208 §2.4: with a null envelope sender, SPF checks the HELO name instead.
    String spfDomain = received.envelopeDomain() != null ? received.envelopeDomain() : received.helo().toLowerCase(Locale.ROOT);
    String ip = received.clientIp();
    result.fact("Test message", "received by the SMTP sink " + received.delayMs() / 1000 + "s after it was sent");
    result.fact("Sending server", ip + " (HELO " + received.helo() + ")");
    result.fact("Envelope sender", received.envelopeFrom().isEmpty() ? "<> (empty: SPF checks the HELO name)" : received.envelopeFrom());

    String fix = "Add \"" + (ip.contains(":") ? "ip6:" : "ip4:") + ip + "\" to " + spfDomain + "'s record, or the include your provider documents.";
    SpfResult spf = new SpfEvaluator(resolver).evaluate(ip, spfDomain);
    String sent = "The test message was sent from " + ip;
    switch (spf) {
      case PASS -> result.finding(CheckStatus.OK, "The test message passed SPF: it was sent from " + ip + ", which "
          + spfDomain + "'s record allows.");
      case FAIL -> result.finding(CheckStatus.ERROR, sent + ", which " + spfDomain + "'s record doesn't allow: it failed SPF, "
          + "so many servers will reject your mail. " + fix);
      case SOFTFAIL -> result.finding(CheckStatus.WARNING, sent + ", which " + spfDomain + "'s record doesn't allow: it soft-failed "
          + "SPF, so your mail may be marked as spam. " + fix);
      case NEUTRAL -> result.finding(CheckStatus.WARNING, sent + ", which " + spfDomain + "'s record doesn't mention: SPF is "
          + "neutral, it doesn't vouch for your mail. " + fix);
      case NONE -> result.finding(CheckStatus.WARNING, sent + " with " + spfDomain + " as envelope sender domain, which has no "
          + "SPF record: nothing vouches for your mail.");
      case PERMERROR -> result.finding(CheckStatus.ERROR, "The test message got a permanent SPF error: " + spfDomain
          + "'s record is broken, receiving servers can't evaluate it.");
      case TEMPERROR -> result.finding(CheckStatus.WARNING, "Could not evaluate SPF for the test message: a DNS lookup failed. "
          + "Try again later.");
    }

    if (!spfDomain.equals(fromDomain) && !organizationalDomain(spfDomain).equals(organizationalDomain(fromDomain))) {
      result.finding(CheckStatus.INFO, "SPF was checked against " + spfDomain + ", the envelope sender domain, not " + fromDomain
          + ": that's usual with a mail provider, but then SPF doesn't count toward " + fromDomain + "'s DMARC, which relies "
          + "on DKIM instead.");
    }
  }

  private static String organizationalDomain(String domain) {
    return DmarcEvaluator.organizationalDomainOf(domain).orElse(domain.toLowerCase(Locale.ROOT));
  }

  private static final class Analysis {
    int lookups;
    boolean ptrUsed;
    final List<CheckFindingDto> findings = new ArrayList<>();
  }

  /**
   * Walks a record and everything it includes or redirects to, counting DNS lookups and noting
   * problems. {@code path} holds the domains currently being walked, to stop include loops.
   *
   * @return the qualifier of the {@code all} that applies to this record — its own, or its
   *     redirect target's — or null if there's none.
   */
  private Character analyze(String domain, String record, int depth, Set<String> path, Analysis analysis) {
    if (depth > MAX_DEPTH) {
      analysis.findings.add(new CheckFindingDto(CheckStatus.ERROR, "Includes/redirects nested more than " + MAX_DEPTH + " levels deep."));
      return null;
    }
    path.add(domain);
    Character all = null;
    String redirect = null;
    String[] terms = record.trim().split("\\s+");
    for (int i = 1; i < terms.length; i++) { // terms[0] is "v=spf1"
      String term = terms[i];
      Matcher modifier = MODIFIER.matcher(term);
      if (modifier.matches() && "+-~?".indexOf(term.charAt(0)) < 0) {
        if (modifier.group(1).equalsIgnoreCase("redirect")) redirect = modifier.group(2);
        continue; // exp= and unknown modifiers: ignored, as receivers do (RFC 7208 §6).
      }
      Matcher m = MECHANISM.matcher(term);
      if (!m.matches()) {
        analysis.findings.add(new CheckFindingDto(CheckStatus.ERROR, "Unrecognized term \"" + term + "\" in " + domain
            + "'s record: receiving servers treat the whole record as a permanent error (PermError)."));
        continue;
      }
      String name = m.group(2).toLowerCase(Locale.ROOT);
      switch (name) {
        case "all" -> {
          if (all == null) all = m.group(1) == null ? '+' : m.group(1).charAt(0);
        }
        case "a", "mx", "exists" -> analysis.lookups++;
        case "ptr" -> {
          analysis.lookups++;
          analysis.ptrUsed = true;
        }
        case "include" -> {
          analysis.lookups++;
          follow(domain, "include:" + m.group(3), m.group(3), depth, path, analysis);
        }
        default -> {
          // ip4/ip6: no DNS lookup.
        }
      }
    }
    if (redirect != null) {
      analysis.lookups++;
      // RFC 7208 §6.1: redirect only applies when the record has no "all".
      Character redirected = follow(domain, "redirect=" + redirect, redirect, depth, path, analysis);
      if (all == null) all = redirected;
    }
    path.remove(domain);
    return all;
  }

  /** Analyzes an included/redirected domain's record. @return its {@code all} qualifier, see {@link #analyze}. */
  private Character follow(String from, String term, String target, int depth, Set<String> path, Analysis analysis) {
    if (target == null || target.isBlank()) {
      analysis.findings.add(new CheckFindingDto(CheckStatus.ERROR, "\"" + term + "\" in " + from + "'s record has no domain."));
      return null;
    }
    if (target.contains("%")) {
      analysis.findings.add(new CheckFindingDto(CheckStatus.INFO, "\"" + term + "\" uses a macro: its record couldn't be "
          + "checked, nor its DNS lookups counted."));
      return null;
    }
    String targetDomain = target.toLowerCase(Locale.ROOT);
    if (path.contains(targetDomain)) {
      analysis.findings.add(new CheckFindingDto(CheckStatus.ERROR, "\"" + term + "\" in " + from + "'s record loops back to a "
          + "record already being evaluated."));
      return null;
    }
    List<String> records;
    try {
      records = spfRecords(targetDomain);
    } catch (SpfDnsException e) {
      analysis.findings.add(new CheckFindingDto(CheckStatus.WARNING, "Could not fetch the record behind \"" + term + "\" ("
          + e.getMessage() + "): its DNS lookups aren't counted."));
      return null;
    }
    if (records.size() != 1) {
      analysis.findings.add(new CheckFindingDto(CheckStatus.ERROR, "\"" + term + "\" in " + from + "'s record points to a domain with "
          + (records.isEmpty() ? "no" : records.size()) + " SPF records: receiving servers treat this as a permanent error (PermError)."));
      return null;
    }
    return analyze(targetDomain, records.get(0), depth + 1, path, analysis);
  }

  private List<String> spfRecords(String domain) throws SpfDnsException {
    return resolver.lookupTxt(domain).stream()
        .filter(txt -> txt.regionMatches(true, 0, "v=spf1", 0, 6) && (txt.length() == 6 || Character.isWhitespace(txt.charAt(6))))
        .toList();
  }
}
