package net.pieroxy.mom.detection.reputation;

import net.pieroxy.mom.config.general.ReputationListConfig;

import java.util.List;
import java.util.Optional;

/**
 * The reputation lists the setup wizard offers — the same as config.example.json's (checked by
 * RecommendedReputationListsTest), which DefaultSpamRules builds its reputation rules on.
 */
public final class RecommendedReputationLists {
  /**
   * @param approxEntries a rough size, for the wizard to show before anything is downloaded
   *     (measured in 2026-10).
   */
  public record Recommended(ReputationListConfig config, String description, String approxEntries) {
  }

  public final static List<Recommended> ALL = List.of(
      recommended("spamhaus-drop", ReputationListType.IP_CIDR, "https://www.spamhaus.org/drop/drop.txt",
          "Spamhaus DROP: IP ranges hijacked or operated by professional spammers.", "~1,700 IP ranges"),
      recommended("blocklist-de-mail", ReputationListType.IP_CIDR,
          "https://raw.githubusercontent.com/firehol/blocklist-ipsets/master/blocklist_de_mail.ipset",
          "blocklist.de: IPs recently reported for attacking mail servers.", "~14,000 IPs"),
      recommended("hagezi-tif-mini", ReputationListType.DOMAIN,
          "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/wildcard/tif.mini-onlydomains.txt",
          "HaGeZi Threat Intelligence (mini): domains used for malware, phishing and scams.", "~240,000 domains"),
      recommended("blocklist-project-phishing", ReputationListType.DOMAIN,
          "https://blocklistproject.github.io/Lists/alt-version/phishing-nl.txt",
          "Blocklist Project: known phishing domains.", "~190,000 domains"),
      recommended("disposable-email-domains", ReputationListType.DOMAIN,
          "https://raw.githubusercontent.com/disposable-email-domains/disposable-email-domains/master/disposable_email_blocklist.conf",
          "Disposable email providers: throwaway addresses, often used for spam.", "~9,000 domains"),
      recommended("hagezi-nrd7", ReputationListType.DOMAIN, "https://cdn.jsdelivr.net/gh/hagezi/nrd@latest/domains/nrd7.txt",
          "HaGeZi newly registered domains: registered in the last 7 days, a common trait of spam. By far the largest list (memory).",
          "~3,300,000 domains"));

  private RecommendedReputationLists() {
  }

  public static Optional<Recommended> byId(String id) {
    return ALL.stream().filter(r -> r.config().getId().equals(id)).findFirst();
  }

  private static Recommended recommended(String id, ReputationListType type, String url, String description, String approxEntries) {
    ReputationListConfig config = new ReputationListConfig();
    config.setId(id);
    config.setType(type);
    config.setUrl(url);
    config.setRefreshHours(24);
    config.setScore(1.0);
    return new Recommended(config, description, approxEntries);
  }
}
