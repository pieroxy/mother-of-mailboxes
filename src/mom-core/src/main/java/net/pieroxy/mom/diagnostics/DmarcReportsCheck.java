package net.pieroxy.mom.diagnostics;

import net.pieroxy.mom.detection.dmarc.DmarcDnsException;
import net.pieroxy.mom.detection.dmarc.DmarcDnsResolver;
import net.pieroxy.mom.detection.dmarc.DmarcEvaluator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks a domain's DMARC aggregate reporting (RFC 7489 §7.2): the {@code rua=} tag of its
 * DMARC record, asking receivers for daily reports of who sends mail as the domain and how it
 * fares. How much a missing one matters depends on the policy: with {@code p=none} it's the whole
 * point of the record, with {@code p=reject} only a nice-to-have. When present, each destination
 * must be a valid {@code mailto:} address able to receive mail, and — when outside the domain's
 * organization — authorized by its own domain (RFC 7489 §7.1), or receivers silently send nothing.
 */
public class DmarcReportsCheck {
  // "mailto:" address, then an optional size limit such as "!10m".
  private static final Pattern MAILTO = Pattern.compile("(?i)^mailto:([^!@\\s]+@([^!\\s]+))(?:!(\\d+[kmgt]?))?$");

  private final DmarcDnsResolver dmarcDns;
  private final MxDnsResolver mxDns;

  public DmarcReportsCheck(DmarcDnsResolver dmarcDns, MxDnsResolver mxDns) {
    this.dmarcDns = dmarcDns;
    this.mxDns = mxDns;
  }

  public DeliveryCheckResultDto run(String fromDomain) {
    DeliveryCheckResultDto result = new DeliveryCheckResultDto(DeliveryCheck.DMARC_REPORTS).fact("Domain", fromDomain);
    DmarcCheck.Found found;
    DeliveryCheckResultDto scratch = new DeliveryCheckResultDto(DeliveryCheck.DMARC);
    try {
      found = new DmarcCheck(dmarcDns, null, null).find(fromDomain, scratch);
    } catch (DmarcDnsException e) {
      return result.finding(CheckStatus.WARNING, "Could not query the DNS for " + fromDomain + "'s DMARC record ("
          + e.getMessage() + "). Try again later.").conclude("");
    }
    String policy = found == null ? null : DmarcCheck.effectivePolicy(found);
    if (policy == null) {
      return DeliveryCheckResultDto.skipped(DeliveryCheck.DMARC_REPORTS, "No valid DMARC record to ask for reports in: see the DMARC check.");
    }
    String policyDomain = found.publishedAt().substring("_dmarc.".length());
    result.fact("Policy", "p=" + policy + (found.inherited() ? " (inherited from " + policyDomain + ")" : ""));

    String rua = found.tags().get("rua");
    if (rua == null || rua.isBlank()) {
      noReports(result, policy, found.inherited() ? policyDomain : fromDomain);
    } else {
      result.fact("Aggregate reports (rua)", rua);
      checkDestinations(result, policyDomain, rua);
    }

    String ruf = found.tags().get("ruf");
    if (ruf != null && !ruf.isBlank()) {
      result.fact("Failure reports (ruf)", ruf);
      result.finding(CheckStatus.INFO, "ruf= asks for per-message failure reports: most big receivers no longer send them, for "
          + "privacy reasons. Harmless, but don't count on them.");
    }
    String ri = found.tags().get("ri");
    if (ri != null && !ri.equals("86400")) {
      result.finding(CheckStatus.INFO, "ri=" + ri + " asks for reports every " + ri + " seconds: most receivers ignore it and send "
          + "one a day anyway.");
    }
    String summary;
    if (rua != null && !rua.isBlank()) {
      summary = "DMARC aggregate reports are set up for " + fromDomain + ": they go to " + rua + ".";
    } else if (policy.equals("reject")) {
      summary = "No DMARC aggregate reports for " + fromDomain + ", but p=reject protects it without them.";
    } else {
      summary = "No DMARC aggregate reports for " + fromDomain + ": you won't see what p=quarantine sends to spam.";
    }
    return result.conclude(summary);
  }

  private static void noReports(DeliveryCheckResultDto result, String policy, String domain) {
    String add = " Add rua=mailto:<an address of yours> to the DMARC record of " + domain + ".";
    switch (policy) {
      case "none" -> result.finding(CheckStatus.WARNING, "No aggregate reports (no rua=), with p=none: the policy only monitors, "
          + "and without reports nobody sees what it monitors — which servers send mail as your domain, and whether your own mail "
          + "passes. That's what you need before tightening the policy." + add);
      case "quarantine" -> result.finding(CheckStatus.INFO, "No aggregate reports (no rua=): p=quarantine protects you, but you "
          + "won't see what it sends to spam — such as a forgotten service legitimately sending mail as your domain." + add);
      default -> {
        result.finding(CheckStatus.OK, "No aggregate reports (no rua=), but p=reject protects your domain without them.");
        result.finding(CheckStatus.INFO, "Reports would still show you who tries to send mail as your domain, and whether all your "
            + "own mail passes." + add);
      }
    }
  }

  private void checkDestinations(DeliveryCheckResultDto result, String policyDomain, String rua) {
    for (String raw : rua.split(",")) {
      String destination = raw.trim();
      Matcher m = MAILTO.matcher(destination);
      if (!m.matches()) {
        result.finding(CheckStatus.ERROR, "The report destination \"" + destination + "\" isn't a valid mailto: address: receivers "
            + "ignore it. Use rua=mailto:address@domain.");
        continue;
      }
      String address = m.group(1);
      String domain = m.group(2).toLowerCase(Locale.ROOT);
      if (!canReceive(result, address, domain)) continue;

      if (organizationalDomain(domain).equals(organizationalDomain(policyDomain))) {
        result.finding(CheckStatus.OK, "Reports go to " + address + ".");
        continue;
      }
      // RFC 7489 §7.1: a destination in another organization must agree to receive the reports.
      String authorization = policyDomain + "._report._dmarc." + domain;
      try {
        boolean authorized = dmarcDns.lookupTxt(authorization).stream().anyMatch(t -> t.regionMatches(true, 0, "v=DMARC1", 0, 8));
        if (authorized) {
          result.finding(CheckStatus.OK, "Reports go to " + address + ", and " + domain + " agrees to receive them for "
              + policyDomain + ".");
        } else {
          result.finding(CheckStatus.ERROR, "Reports go to " + address + ", outside " + policyDomain + ", but " + domain + " doesn't "
              + "agree to receive them: receivers check for a TXT record \"v=DMARC1\" at " + authorization + ", and silently send "
              + "nothing without it. Publish it in " + domain + "'s DNS.");
        }
      } catch (DmarcDnsException e) {
        result.finding(CheckStatus.WARNING, "Could not check whether " + domain + " agrees to receive the reports (" + e.getMessage()
            + "). Try again later.");
      }
    }
  }

  /** Whether reports sent to {@code address} can be delivered: its domain has an MX, or at least an address. */
  private boolean canReceive(DeliveryCheckResultDto result, String address, String domain) {
    try {
      List<MxDnsResolver.Mx> mx = mxDns.lookupMx(domain);
      if (mx.stream().anyMatch(r -> r.host().isEmpty())) {
        result.finding(CheckStatus.ERROR, "Reports go to " + address + ", but " + domain + " accepts no mail (null MX): they're lost.");
        return false;
      }
      if (!mx.isEmpty()) return true;
      List<String> addresses = new ArrayList<>(mxDns.lookupA(domain));
      addresses.addAll(mxDns.lookupAaaa(domain));
      if (addresses.isEmpty()) {
        result.finding(CheckStatus.ERROR, "Reports go to " + address + ", but " + domain + " has no MX record nor address: they "
            + "can't be delivered.");
        return false;
      }
      return true;
    } catch (MxDnsResolver.DnsLookupException e) {
      result.finding(CheckStatus.WARNING, "Could not check that " + domain + " receives mail (" + e.getMessage() + ").");
      return false;
    }
  }

  private static String organizationalDomain(String domain) {
    return DmarcEvaluator.organizationalDomainOf(domain).orElse(domain.toLowerCase(Locale.ROOT));
  }
}
