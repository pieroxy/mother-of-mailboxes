package net.pieroxy.imf.detection.spf;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SPF evaluator (RFC 7208): checks whether an IP is authorized to send mail on behalf of a
 * given domain, by querying DNS through an injected {@link SpfDnsResolver}.
 * <p>
 * Covers the {@code all}, {@code ip4}, {@code ip6}, {@code a}, {@code mx}, {@code include}
 * mechanisms and the {@code redirect} modifier. Does not support macros ({@code %{...}}): a
 * term containing one is skipped (fine log) rather than failing the whole evaluation, since
 * they're mostly used in {@code exp=} (never read here) and very rarely used elsewhere in
 * practice. {@code ptr} is deliberately ignored (never matched), as recommended by the RFC, to
 * avoid an expensive and unreliable reverse DNS resolution.
 * <p>
 * Two distinct kinds of error: {@link SpfDnsException} (a temporary DNS failure, e.g. timeout)
 * yields {@link SpfResult#TEMPERROR}; a malformed record, an exceeded lookup budget, or an
 * "include"/"redirect" pointing to a domain without SPF ({@link SpfPermErrorException}) yields
 * {@link SpfResult#PERMERROR} — retrying later wouldn't change anything in that case.
 */
public class SpfEvaluator {
  // RFC 7208 §4.6.4: beyond 10 mechanisms/modifiers that trigger a DNS query (include, a, mx,
  // ptr, exists, redirect), evaluation must stop with a PermError.
  private static final int MAX_DNS_LOOKUPS = 10;
  private static final int MAX_RECURSION_DEPTH = 10;
  private static final int MAX_MX_HOSTS_CHECKED = 10;

  private static final Pattern IP_LITERAL = Pattern.compile("^[0-9a-fA-F:.]+$");
  private static final Pattern MECHANISM = Pattern.compile(
          "^([+\\-~?])?(all|include|a|mx|ptr|ip4|ip6|exists)(?::([^/]+))?(?:/(\\d+))?(?:/(\\d+))?$",
          Pattern.CASE_INSENSITIVE);

  private final SpfDnsResolver resolver;
  private final Logger defaultLogger = Logger.getLogger(SpfEvaluator.class.getName());

  public SpfEvaluator(SpfDnsResolver resolver) {
    this.resolver = resolver;
  }

  /** @return the SPF result for {@code ip} sending mail on behalf of {@code domain}. */
  public SpfResult evaluate(String ip, String domain) {
    return evaluate(ip, domain, defaultLogger);
  }

  /**
   * Like {@link #evaluate(String, String)}, but logs the evaluation details (FINE level) — the
   * SPF record found, mechanism by mechanism, final result — to the given logger instead of the
   * class's own default one. Lets the caller (typically a matcher, whose log level is
   * configured per rule via the {@code logLevel} JSON field) make this trace visible without
   * touching the global logging configuration.
   */
  public SpfResult evaluate(String ip, String domain, Logger logger) {
    if (ip == null || domain == null || domain.isBlank()) return SpfResult.NONE;
    InetAddress address;
    try {
      address = parseLiteralIp(ip);
    } catch (UnknownHostException e) {
      logger.fine(() -> "Not a valid IP literal, cannot evaluate SPF: " + ip);
      return SpfResult.NONE;
    }
    logger.fine(() -> "Evaluating SPF for ip=" + ip + " domain=" + domain);
    try {
      SpfResult result = check(address, domain, new int[]{0}, 0, logger);
      logger.fine(() -> "SPF result for ip=" + ip + " domain=" + domain + ": " + result.getCode());
      return result;
    } catch (SpfPermErrorException e) {
      logger.fine(() -> "SPF record error for domain " + domain + ": " + e.getMessage());
      return SpfResult.PERMERROR;
    } catch (SpfDnsException e) {
      logger.log(Level.FINE, "SPF DNS lookup failed for domain " + domain, e);
      return SpfResult.TEMPERROR;
    }
  }

  private SpfResult check(InetAddress ip, String domain, int[] lookupCount, int depth, Logger logger) throws SpfDnsException, SpfPermErrorException {
    if (depth > MAX_RECURSION_DEPTH) throw new SpfPermErrorException("Too many levels of include/redirect");

    List<String> spfRecords = resolver.lookupTxt(domain).stream()
            .filter(SpfEvaluator::isSpfRecord)
            .toList();
    if (spfRecords.isEmpty()) {
      logger.fine(() -> "No SPF TXT record for domain " + domain);
      return SpfResult.NONE;
    }
    if (spfRecords.size() > 1) throw new SpfPermErrorException("Multiple SPF records for domain " + domain);

    String spfRecord = spfRecords.get(0);
    logger.fine(() -> "SPF record for domain " + domain + ": " + spfRecord);
    String[] terms = spfRecord.trim().split("\\s+");
    String redirect = null;

    for (int i = 1; i < terms.length; i++) { // terms[0] is "v=spf1"
      String term = terms[i];
      if (term.isEmpty()) continue;

      int eq = term.indexOf('=');
      if (eq > 0 && "+-~?".indexOf(term.charAt(0)) < 0) {
        String name = term.substring(0, eq);
        if (name.equalsIgnoreCase("redirect")) redirect = term.substring(eq + 1);
        // exp= and any other unknown modifier: ignored (RFC 7208 §6).
        continue;
      }

      Matcher m = MECHANISM.matcher(term);
      if (!m.matches()) throw new SpfPermErrorException("Unrecognized SPF term: " + term);

      if (m.group(3) != null && m.group(3).contains("%")) {
        logger.fine(() -> "Skipping unsupported macro in SPF term: " + term);
        continue;
      }

      char qualifierChar = m.group(1) != null ? m.group(1).charAt(0) : '+';
      SpfResult qualifier = qualifierResult(qualifierChar);
      String name = m.group(2).toLowerCase();
      String arg = m.group(3);
      Integer cidr1 = m.group(4) != null ? Integer.valueOf(m.group(4)) : null;
      Integer cidr2 = m.group(5) != null ? Integer.valueOf(m.group(5)) : null;

      boolean matched = switch (name) {
        case "all" -> true;
        case "ip4", "ip6" -> matchesIpLiteral(ip, arg, cidr1);
        case "a" -> matchesResolvedHost(ip, arg != null ? arg : domain, cidr1, cidr2, lookupCount);
        case "mx" -> matchesMx(ip, arg != null ? arg : domain, cidr1, cidr2, lookupCount);
        case "include" -> matchesInclude(ip, arg, lookupCount, depth, logger);
        case "exists" -> matchesExists(arg, lookupCount);
        case "ptr" -> false; // deliberately unsupported, see the class javadoc
        default -> throw new SpfPermErrorException("Unsupported SPF mechanism: " + name);
      };
      logger.fine(() -> "mechanism " + term + " -> " + (matched ? "match (" + qualifier.getCode() + ")" : "no match"));
      if (matched) return qualifier;
    }

    if (redirect != null) {
      if (redirect.contains("%")) throw new SpfPermErrorException("Unsupported macro in redirect: " + redirect);
      if (++lookupCount[0] > MAX_DNS_LOOKUPS) throw new SpfPermErrorException("Too many DNS lookups evaluating SPF record");
      String redirectTarget = redirect;
      logger.fine(() -> "No mechanism matched, following redirect=" + redirectTarget);
      SpfResult redirected = check(ip, redirect, lookupCount, depth + 1, logger);
      // RFC 7208 §6.1: if the redirect target domain has no SPF record, that's a PermError.
      if (redirected == SpfResult.NONE) throw new SpfPermErrorException("redirect=" + redirect + " has no SPF record");
      return redirected;
    }
    return SpfResult.NEUTRAL;
  }

  private SpfResult qualifierResult(char c) {
    return switch (c) {
      case '-' -> SpfResult.FAIL;
      case '~' -> SpfResult.SOFTFAIL;
      case '?' -> SpfResult.NEUTRAL;
      default -> SpfResult.PASS;
    };
  }

  private boolean matchesIpLiteral(InetAddress ip, String literal, Integer cidr) throws SpfPermErrorException {
    if (literal == null) throw new SpfPermErrorException("ip4/ip6 mechanism without a value");
    InetAddress network;
    try {
      network = parseLiteralIp(literal);
    } catch (UnknownHostException e) {
      throw new SpfPermErrorException("Invalid IP literal in SPF record: " + literal);
    }
    int prefix = cidr != null ? cidr : network.getAddress().length * 8;
    return isInCidr(ip, network, prefix);
  }

  private boolean matchesResolvedHost(InetAddress ip, String domain, Integer cidr4, Integer cidr6, int[] lookupCount) throws SpfDnsException, SpfPermErrorException {
    if (++lookupCount[0] > MAX_DNS_LOOKUPS) throw new SpfPermErrorException("Too many DNS lookups evaluating SPF record");
    boolean isV4 = ip.getAddress().length == 4;
    List<String> addresses = isV4 ? resolver.lookupA(domain) : resolver.lookupAaaa(domain);
    int prefix = isV4 ? (cidr4 != null ? cidr4 : 32) : (cidr6 != null ? cidr6 : 128);
    for (String candidate : addresses) {
      try {
        if (isInCidr(ip, InetAddress.getByName(candidate), prefix)) return true;
      } catch (UnknownHostException ignored) {
        // shouldn't happen: candidate comes from the resolver, already a valid literal
      }
    }
    return false;
  }

  private boolean matchesMx(InetAddress ip, String domain, Integer cidr4, Integer cidr6, int[] lookupCount) throws SpfDnsException, SpfPermErrorException {
    if (++lookupCount[0] > MAX_DNS_LOOKUPS) throw new SpfPermErrorException("Too many DNS lookups evaluating SPF record");
    List<String> mxHosts = resolver.lookupMx(domain);
    int checked = 0;
    for (String host : mxHosts) {
      if (++checked > MAX_MX_HOSTS_CHECKED) break;
      boolean isV4 = ip.getAddress().length == 4;
      List<String> addresses = isV4 ? resolver.lookupA(host) : resolver.lookupAaaa(host);
      int prefix = isV4 ? (cidr4 != null ? cidr4 : 32) : (cidr6 != null ? cidr6 : 128);
      for (String candidate : addresses) {
        try {
          if (isInCidr(ip, InetAddress.getByName(candidate), prefix)) return true;
        } catch (UnknownHostException ignored) {
        }
      }
    }
    return false;
  }

  private boolean matchesInclude(InetAddress ip, String includedDomain, int[] lookupCount, int depth, Logger logger) throws SpfDnsException, SpfPermErrorException {
    if (includedDomain == null) throw new SpfPermErrorException("include mechanism without a value");
    if (++lookupCount[0] > MAX_DNS_LOOKUPS) throw new SpfPermErrorException("Too many DNS lookups evaluating SPF record");
    SpfResult included = check(ip, includedDomain, lookupCount, depth + 1, logger);
    // RFC 7208 §5.2: only a "pass" from the included domain makes the "include" match; fail/
    // softfail/neutral make evaluation continue to the next mechanism; none/permerror
    // invalidate the whole enclosing record.
    return switch (included) {
      case PASS -> true;
      case FAIL, SOFTFAIL, NEUTRAL -> false;
      default -> throw new SpfPermErrorException("include:" + includedDomain + " resolved to " + included);
    };
  }

  private boolean matchesExists(String domain, int[] lookupCount) throws SpfDnsException, SpfPermErrorException {
    if (domain == null) throw new SpfPermErrorException("exists mechanism without a value");
    if (++lookupCount[0] > MAX_DNS_LOOKUPS) throw new SpfPermErrorException("Too many DNS lookups evaluating SPF record");
    return !resolver.lookupA(domain).isEmpty();
  }

  private static boolean isSpfRecord(String txt) {
    return txt.regionMatches(true, 0, "v=spf1", 0, 6) && (txt.length() == 6 || Character.isWhitespace(txt.charAt(6)));
  }

  private static InetAddress parseLiteralIp(String literal) throws UnknownHostException {
    if (literal == null || !IP_LITERAL.matcher(literal).matches()) {
      throw new UnknownHostException("Not an IP literal: " + literal);
    }
    return InetAddress.getByName(literal);
  }

  private static boolean isInCidr(InetAddress ip, InetAddress network, int prefixLength) {
    byte[] ipBytes = ip.getAddress();
    byte[] netBytes = network.getAddress();
    if (ipBytes.length != netBytes.length) return false; // different address families (v4 vs v6)
    if (prefixLength < 0 || prefixLength > ipBytes.length * 8) return false;

    int fullBytes = prefixLength / 8;
    int remainingBits = prefixLength % 8;
    for (int i = 0; i < fullBytes; i++) {
      if (ipBytes[i] != netBytes[i]) return false;
    }
    if (remainingBits > 0) {
      int mask = (0xFF << (8 - remainingBits)) & 0xFF;
      if ((ipBytes[fullBytes] & mask) != (netBytes[fullBytes] & mask)) return false;
    }
    return true;
  }
}
