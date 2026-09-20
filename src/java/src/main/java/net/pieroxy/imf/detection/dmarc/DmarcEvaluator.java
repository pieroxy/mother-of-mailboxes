package net.pieroxy.imf.detection.dmarc;

import com.google.common.net.InternetDomainName;
import net.pieroxy.imf.detection.dkim.DkimVerifier;
import net.pieroxy.imf.detection.spf.SpfEvaluator;

import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * DMARC evaluator (RFC 7489): doesn't redo SPF or DKIM itself — it takes their already-computed
 * results (see {@link SpfEvaluator}, {@link DkimVerifier})
 * and determines whether either one is "aligned" with the domain shown in {@code From:}, as
 * well as the effective policy published by that domain (see {@link DmarcPolicy}).
 * <p>
 * DMARC passes if SPF succeeded AND is aligned, OR if DKIM succeeded AND is aligned (RFC 7489
 * §3.1) — either one is enough. "Aligned" means: the exact same domain (strict mode,
 * {@code adkim=s}/{@code aspf=s}), or the same organizational domain (relaxed mode, the
 * default) — computed via the Public Suffix List (Guava's {@code InternetDomainName}), not a
 * naive comparison of the last two labels: for {@code mail.example.co.uk}, the organizational
 * domain is {@code example.co.uk}, not {@code co.uk} (itself a public suffix shared by millions
 * of unrelated domains).
 * <p>
 * The record is looked up at the exact domain first, then — if absent there — at the
 * organizational domain (RFC 7489 §6.6.3), to cover the case of a subdomain that has no DMARC
 * record of its own; in that second case, it's the organizational record's {@code sp=} tag that
 * governs the policy (falling back to {@code p=} if {@code sp=} is absent), not {@code p=}
 * directly.
 */
public class DmarcEvaluator {
  private final DmarcDnsResolver resolver;
  private final Logger defaultLogger = Logger.getLogger(DmarcEvaluator.class.getName());

  public DmarcEvaluator(DmarcDnsResolver resolver) {
    this.resolver = resolver;
  }

  /**
   * @param fromDomain domain of the displayed From: header (the identity DMARC protects).
   * @param spfPassed  true if SPF returned "pass" for this message.
   * @param spfDomain  the domain SPF actually verified (see SpfIdentityExtractor), non-null if spfPassed is true.
   * @param dkimPassingDomains the domains (d=) of every DKIM signature that verified successfully.
   */
  public DmarcResult evaluate(String fromDomain, boolean spfPassed, String spfDomain, List<String> dkimPassingDomains) {
    return evaluate(fromDomain, spfPassed, spfDomain, dkimPassingDomains, defaultLogger);
  }

  /** Same as {@link #evaluate(String, boolean, String, List)}, but logs (FINE level) to the given logger. */
  public DmarcResult evaluate(String fromDomain, boolean spfPassed, String spfDomain, List<String> dkimPassingDomains, Logger logger) {
    return evaluateDetailed(fromDomain, spfPassed, spfDomain, dkimPassingDomains, logger).result();
  }

  /** Same as {@link #evaluate(String, boolean, String, List, Logger)}, but also exposes the published policy. */
  public DmarcEvaluation evaluateDetailed(String fromDomain, boolean spfPassed, String spfDomain, List<String> dkimPassingDomains, Logger logger) {
    if (fromDomain == null || fromDomain.isBlank()) {
      return new DmarcEvaluation(DmarcResult.NONE, DmarcPolicy.UNPUBLISHED);
    }
    try {
      EffectiveRecord found = findRecord(fromDomain, logger);
      if (found == null) {
        logger.fine(() -> "No DMARC record for " + fromDomain + " or its organizational domain");
        return new DmarcEvaluation(DmarcResult.NONE, DmarcPolicy.UNPUBLISHED);
      }
      boolean spfAligned = spfPassed && spfDomain != null && domainsAligned(fromDomain, spfDomain, found.record.strictSpf);
      boolean dkimAligned = dkimPassingDomains.stream().anyMatch(d -> domainsAligned(fromDomain, d, found.record.strictDkim));
      logger.fine(() -> "DMARC alignment for from=" + fromDomain + ": spfAligned=" + spfAligned + " dkimAligned=" + dkimAligned);
      DmarcResult result = (spfAligned || dkimAligned) ? DmarcResult.PASS : DmarcResult.FAIL;
      DmarcPolicy policy = DmarcPolicy.valueOf(found.effectivePolicy.toUpperCase());
      return new DmarcEvaluation(result, policy);
    } catch (DmarcPermErrorException e) {
      logger.fine(() -> "DMARC record error for " + fromDomain + ": " + e.getMessage());
      return new DmarcEvaluation(DmarcResult.PERMERROR, DmarcPolicy.PERMERROR);
    } catch (DmarcDnsException e) {
      logger.log(Level.FINE, "DMARC DNS lookup failed for " + fromDomain, e);
      return new DmarcEvaluation(DmarcResult.TEMPERROR, DmarcPolicy.TEMPERROR);
    }
  }

  private EffectiveRecord findRecord(String fromDomain, Logger logger) throws DmarcDnsException, DmarcPermErrorException {
    DmarcRecord exact = fetchValidRecord(fromDomain);
    if (exact != null) return new EffectiveRecord(exact, exact.policy);

    Optional<String> orgDomain = organizationalDomainOf(fromDomain);
    if (orgDomain.isPresent() && !orgDomain.get().equalsIgnoreCase(fromDomain)) {
      logger.fine(() -> "No DMARC record at " + fromDomain + ", trying organizational domain " + orgDomain.get());
      DmarcRecord viaOrg = fetchValidRecord(orgDomain.get());
      // The organizational record's sp= (falling back to p=) applies to subdomains that have
      // no record of their own (RFC 7489 §6.3) — not p=, which only ever concerned that domain.
      if (viaOrg != null) return new EffectiveRecord(viaOrg, viaOrg.subdomainPolicy);
    }
    return null;
  }

  private DmarcRecord fetchValidRecord(String domain) throws DmarcDnsException, DmarcPermErrorException {
    List<String> candidates = resolver.lookupTxt("_dmarc." + domain).stream()
            .filter(DmarcEvaluator::looksLikeDmarcRecord)
            .toList();
    if (candidates.isEmpty()) return null;
    if (candidates.size() > 1) {
      throw new DmarcPermErrorException("Multiple DMARC records for " + domain);
    }
    return parseRecord(candidates.get(0), domain);
  }

  private static boolean looksLikeDmarcRecord(String txt) {
    return txt.regionMatches(true, 0, "v=DMARC1", 0, 8)
            && (txt.length() == 8 || txt.charAt(8) == ';' || Character.isWhitespace(txt.charAt(8)));
  }

  private static DmarcRecord parseRecord(String txt, String domain) throws DmarcPermErrorException {
    String policy = null;
    String subdomainPolicy = null;
    boolean strictDkim = false;
    boolean strictSpf = false;
    for (String rawTag : txt.split(";")) {
      String tag = rawTag.trim();
      int eq = tag.indexOf('=');
      if (eq < 0) continue;
      String name = tag.substring(0, eq).trim().toLowerCase();
      String value = tag.substring(eq + 1).trim();
      switch (name) {
        case "p" -> policy = requireValidPolicyValue(value, domain, "p");
        case "sp" -> subdomainPolicy = requireValidPolicyValue(value, domain, "sp");
        case "adkim" -> strictDkim = "s".equalsIgnoreCase(value);
        case "aspf" -> strictSpf = "s".equalsIgnoreCase(value);
        default -> {
          // pct=, rua=, ruf=, fo=, ri=, v=...: ignored, not needed to compute pass/fail/policy
          // (only relevant for reporting or enforcement granularity, out of scope here).
        }
      }
    }
    if (policy == null) {
      throw new DmarcPermErrorException("DMARC record for " + domain + " has no p= tag");
    }
    return new DmarcRecord(policy, subdomainPolicy != null ? subdomainPolicy : policy, strictDkim, strictSpf);
  }

  private static String requireValidPolicyValue(String value, String domain, String tagName) throws DmarcPermErrorException {
    String normalized = value.toLowerCase();
    if (!normalized.equals("none") && !normalized.equals("quarantine") && !normalized.equals("reject")) {
      throw new DmarcPermErrorException("DMARC record for " + domain + " has an invalid " + tagName + "=" + value);
    }
    return normalized;
  }

  private static boolean domainsAligned(String fromDomain, String otherDomain, boolean strict) {
    if (strict) return fromDomain.equalsIgnoreCase(otherDomain);
    String fromOrg = organizationalDomainOf(fromDomain).orElse(fromDomain.toLowerCase());
    String otherOrg = organizationalDomainOf(otherDomain).orElse(otherDomain.toLowerCase());
    return fromOrg.equalsIgnoreCase(otherOrg);
  }

  private static Optional<String> organizationalDomainOf(String domain) {
    try {
      InternetDomainName name = InternetDomainName.from(domain);
      if (!name.isUnderPublicSuffix()) return Optional.empty();
      return Optional.of(name.topPrivateDomain().toString());
    } catch (IllegalArgumentException | IllegalStateException e) {
      return Optional.empty();
    }
  }

  private static final class DmarcRecord {
    final String policy;
    final String subdomainPolicy;
    final boolean strictDkim;
    final boolean strictSpf;

    DmarcRecord(String policy, String subdomainPolicy, boolean strictDkim, boolean strictSpf) {
      this.policy = policy;
      this.subdomainPolicy = subdomainPolicy;
      this.strictDkim = strictDkim;
      this.strictSpf = strictSpf;
    }
  }

  private static final class EffectiveRecord {
    final DmarcRecord record;
    final String effectivePolicy;

    EffectiveRecord(DmarcRecord record, String effectivePolicy) {
      this.record = record;
      this.effectivePolicy = effectivePolicy;
    }
  }
}
