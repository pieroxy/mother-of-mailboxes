package net.pieroxy.mom.diagnostics;

import net.pieroxy.mom.config.general.ReputationListConfig;
import net.pieroxy.mom.detection.dmarc.DmarcEvaluator;
import net.pieroxy.mom.detection.reputation.ReputationListType;
import net.pieroxy.mom.detection.reputation.ReputationRegistry;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Looks up the account's sending IP and domains in the reputation lists MOM already downloads to
 * filter incoming mail (General settings' Reputation Lists) — the IP_CIDR ones for the IP, the
 * DOMAIN ones for the domains. A listing's severity follows the list's own score (0 = trusted,
 * 1 = spam). Only as good as the lists configured: the big receivers use their own.
 */
public class ReputationCheck {
  private static final double ERROR_SCORE = 0.7;
  /** From this many listed neighbors in the /24, the neighborhood is worth a warning rather than a note. */
  static final int BAD_NEIGHBORHOOD = 26; // About 10%.
  private static final Pattern DKIM_DOMAIN = Pattern.compile("(?im)^DKIM-Signature:.*?[;\\s]d=([^;\\s]+)");

  private final ReputationRegistry registry;
  private final MxDnsResolver dns;

  public ReputationCheck(ReputationRegistry registry, MxDnsResolver dns) {
    this.registry = registry;
    this.dns = dns;
  }

  /** The IP the account's mail comes from: the test message's, or else — partial — its SMTP server's. */
  public DeliveryCheckResultDto runIp(String smtpHost, Supplier<TestSendOutcome> testSend) {
    List<ReputationListConfig> lists = lists(ReputationListType.IP_CIDR);
    if (lists.isEmpty()) {
      return DeliveryCheckResultDto.skipped(DeliveryCheck.IP_REPUTATION, "No IP reputation list is configured: add some in "
          + "General settings' Reputation Lists.");
    }
    TestSendOutcome outcome = testSend.get();
    if (outcome.kind() == TestSendOutcome.Kind.PENDING) {
      return DeliveryCheckResultDto.inProgress(DeliveryCheck.IP_REPUTATION, outcome.message());
    }

    DeliveryCheckResultDto result = new DeliveryCheckResultDto(DeliveryCheck.IP_REPUTATION);
    List<String> ips = new ArrayList<>();
    if (outcome.kind() == TestSendOutcome.Kind.RECEIVED) {
      ips.add(outcome.received().clientIp());
      result.fact("Sending server", outcome.received().clientIp() + " (from the test message)");
    } else {
      result.setPartial(true);
      if (outcome.kind() == TestSendOutcome.Kind.FAILED) result.finding(CheckStatus.WARNING, "Test send failed: " + outcome.message());
      if (smtpHost != null && !smtpHost.isBlank()) {
        try {
          ips.addAll(dns.lookupA(smtpHost));
          ips.addAll(dns.lookupAaaa(smtpHost));
        } catch (MxDnsResolver.DnsLookupException e) {
          result.finding(CheckStatus.WARNING, "Could not resolve the SMTP server " + smtpHost + " (" + e.getMessage() + ").");
        }
      }
      if (ips.isEmpty()) return result.finding(CheckStatus.INFO, "No address to check: no test message, and the SMTP server's "
          + "address is unknown.").conclude("");
      result.fact("SMTP server", smtpHost + " (" + String.join(", ", ips) + ")");
      result.finding(CheckStatus.INFO, "Without a test message, the SMTP server's addresses are checked instead: they may not be "
          + "the ones your mail is actually delivered from.");
    }
    result.fact("Lists checked", describe(lists));

    for (String ip : ips) {
      if (ip.contains(":")) {
        result.finding(CheckStatus.INFO, ip + " is an IPv6 address: MOM's IP reputation lists only cover IPv4.");
        continue;
      }
      lookUp(result, lists, ip, true);
      checkNeighborhood(result, lists, ip);
    }
    return result.conclude("The address your mail comes from isn't on any of your IP reputation lists.");
  }

  /** The From domain — and with a test message, its envelope and DKIM signing domains — plus their organizational domains. */
  public DeliveryCheckResultDto runDomain(String fromDomain, Supplier<TestSendOutcome> testSend) {
    List<ReputationListConfig> lists = lists(ReputationListType.DOMAIN);
    if (lists.isEmpty()) {
      return DeliveryCheckResultDto.skipped(DeliveryCheck.DOMAIN_REPUTATION, "No domain reputation list is configured: add some "
          + "in General settings' Reputation Lists.");
    }
    TestSendOutcome outcome = testSend.get();
    if (outcome.kind() == TestSendOutcome.Kind.PENDING) {
      return DeliveryCheckResultDto.inProgress(DeliveryCheck.DOMAIN_REPUTATION, outcome.message());
    }

    DeliveryCheckResultDto result = new DeliveryCheckResultDto(DeliveryCheck.DOMAIN_REPUTATION);
    Set<String> domains = new LinkedHashSet<>();
    domains.add(fromDomain.toLowerCase(Locale.ROOT));
    if (outcome.kind() == TestSendOutcome.Kind.RECEIVED) {
      ReceivedTestMessage received = outcome.received();
      if (received.envelopeDomain() != null) domains.add(received.envelopeDomain());
      domains.addAll(dkimDomains(received.raw()));
    } else {
      result.setPartial(true);
      if (outcome.kind() == TestSendOutcome.Kind.FAILED) result.finding(CheckStatus.WARNING, "Test send failed: " + outcome.message());
      result.finding(CheckStatus.INFO, "Without a test message, only the From domain is checked: not the envelope sender and DKIM "
          + "signing domains your mail actually carries.");
    }
    // Lists hold exact names: a listed organizational domain says as much about its subdomains.
    for (String d : List.copyOf(domains)) {
      DmarcEvaluator.organizationalDomainOf(d).map(o -> o.toLowerCase(Locale.ROOT)).ifPresent(domains::add);
    }
    result.fact("Domains checked", String.join(", ", domains));
    result.fact("Lists checked", describe(lists));

    for (String domain : domains) lookUp(result, lists, domain, false);
    return result.conclude("Your domains aren't on any of your domain reputation lists.");
  }

  /**
   * The other addresses of the IP's /24 on each (non-trusted) list the IP itself isn't on: some
   * receivers judge whole neighborhoods, and a heavily listed one says something about the network
   * the mail comes from (a hosting provider tolerating spammers, a residential pool…).
   */
  private void checkNeighborhood(DeliveryCheckResultDto result, List<ReputationListConfig> lists, String ip) {
    String[] octets = ip.split("\\.");
    if (octets.length != 4) return;
    String prefix = octets[0] + "." + octets[1] + "." + octets[2] + ".";
    String network = prefix + "0/24";
    List<String> clean = new ArrayList<>();
    for (ReputationListConfig list : lists) {
      if (list.getScore() <= 0 || registry.getLastModified(list.getId()) == 0) continue;
      Set<String> only = Set.of(list.getId());
      if (registry.ipScore(ip, only).isPresent()) continue; // Listed itself: already reported.
      int listed = 0;
      for (int i = 0; i < 256; i++) {
        String neighbor = prefix + i;
        if (!neighbor.equals(ip) && registry.ipScore(neighbor, only).isPresent()) listed++;
      }
      if (listed == 0) {
        clean.add(list.getId());
      } else if (listed >= BAD_NEIGHBORHOOD) {
        result.finding(CheckStatus.WARNING, listed + " other addresses of " + network + " are on " + list.getId() + ": a bad "
            + "neighborhood. Some receivers judge whole networks, and may treat your mail with suspicion too. If you can, send "
            + "from another network (your provider's relay, another host).");
      } else {
        result.finding(CheckStatus.INFO, listed + (listed == 1 ? " other address" : " other addresses") + " of " + network
            + (listed == 1 ? " is" : " are") + " on " + list.getId() + ": not unusual, but worth keeping an eye on.");
      }
    }
    if (!clean.isEmpty()) result.finding(CheckStatus.OK, "No other address of " + network + " is on " + String.join(", ", clean) + ".");
  }

  private void lookUp(DeliveryCheckResultDto result, List<ReputationListConfig> lists, String value, boolean ip) {
    List<String> clean = new ArrayList<>();
    List<String> notLoaded = new ArrayList<>();
    for (ReputationListConfig list : lists) {
      if (registry.getLastModified(list.getId()) == 0) {
        notLoaded.add(list.getId());
        continue;
      }
      boolean listed = (ip ? registry.ipScore(value, Set.of(list.getId())) : registry.domainScore(value, Set.of(list.getId()))).isPresent();
      if (!listed) {
        clean.add(list.getId());
      } else if (list.getScore() <= 0) {
        result.finding(CheckStatus.OK, value + " is on " + list.getId() + ", a list of trusted senders (score " + list.getScore() + ").");
      } else {
        result.finding(list.getScore() >= ERROR_SCORE ? CheckStatus.ERROR : CheckStatus.WARNING, value + " is listed on "
            + list.getId() + " (score " + list.getScore() + "): servers using that list " + (list.getScore() >= ERROR_SCORE
            ? "are likely to reject or junk your mail" : "count it against your mail") + ". See " + list.getUrl()
            + " for who publishes it, and how to get delisted.");
      }
    }
    if (!clean.isEmpty()) result.finding(CheckStatus.OK, value + " isn't on " + String.join(", ", clean) + ".");
    if (!notLoaded.isEmpty()) {
      result.setPartial(true);
      result.finding(CheckStatus.INFO, "Not checked against " + String.join(", ", notLoaded) + ": never downloaded yet.");
    }
  }

  private List<ReputationListConfig> lists(ReputationListType type) {
    return registry.getConfiguredLists().stream()
        .filter(l -> l.getType() == type)
        .sorted(Comparator.comparing(ReputationListConfig::getId))
        .toList();
  }

  private static String describe(List<ReputationListConfig> lists) {
    return lists.stream().map(ReputationListConfig::getId).collect(Collectors.joining(", "));
  }

  /** The d= of every DKIM-Signature header, valid or not: a listed signing domain hurts either way. */
  static Set<String> dkimDomains(byte[] raw) {
    String text = new String(raw, StandardCharsets.ISO_8859_1);
    int end = text.indexOf("\r\n\r\n");
    String headers = (end < 0 ? text : text.substring(0, end)).replaceAll("\r\n[ \t]+", " ");
    Set<String> domains = new LinkedHashSet<>();
    Matcher m = DKIM_DOMAIN.matcher(headers);
    while (m.find()) domains.add(m.group(1).toLowerCase(Locale.ROOT));
    return domains;
  }
}
