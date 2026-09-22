package net.pieroxy.mom.rules.matchers.implementations;

import net.pieroxy.mom.config.general.MailFilterRuleMatcherConfiguration;
import net.pieroxy.mom.detection.reputation.ReputationMatch;
import net.pieroxy.mom.detection.reputation.ReputationRegistry;
import net.pieroxy.mom.detection.reputation.ReputationRegistryHolder;
import net.pieroxy.mom.rules.matchers.MatchResult;
import net.pieroxy.mom.rules.matchers.Matcher;
import net.pieroxy.mom.detection.spf.SpfIdentityExtractor;

import javax.mail.Message;
import javax.mail.MessagingException;
import java.util.Optional;
import java.util.Set;

/**
 * Compares the connecting IP's reputation score (0=ok, 1=spam, the worst among the
 * {@code IP_CIDR} lists referenced by {@code listIds} that contain it) against a threshold
 * ({@code ">0.5"}, {@code "<=0.2"}...). The lists themselves are downloaded and refreshed once
 * for the whole process, never queried live per message — see {@link ReputationRegistry}.
 */
public class IpReputationMatcher extends Matcher {
  private final ReputationRegistry registry;
  private Set<String> listIds;
  private ReputationThreshold threshold;

  public IpReputationMatcher() {
    this(ReputationRegistryHolder.get());
  }

  /** Visible for tests: allows injecting a registry with no real download. */
  IpReputationMatcher(ReputationRegistry registry) {
    this.registry = registry;
  }

  @Override
  public void setConfig(MailFilterRuleMatcherConfiguration config) {
    super.setConfig(config);
    listIds = config.getListIds();
    if (listIds == null || listIds.isEmpty()) {
      throw new IllegalArgumentException("IP_REPUTATION_EQUALS requires at least one listId");
    }
    threshold = ReputationThreshold.parse(config.getKey(), "IP_REPUTATION_EQUALS");
  }

  @Override
  protected String describeKey() {
    return getConfig().getKey() + " in " + listIds;
  }

  @Override
  public MatchResult matches(Message message) throws MessagingException {
    Optional<String> ip = SpfIdentityExtractor.extractClientIp(message, getLogger());
    if (ip.isEmpty()) {
      getLogger().fine(() -> "no connecting IP found on message, no match against " + threshold);
      return notMatched();
    }
    Optional<ReputationMatch> match = registry.ipScore(ip.get(), listIds);
    if (match.isEmpty()) {
      getLogger().fine(() -> "ip=" + ip.get() + " not present in any referenced reputation list");
      return notMatched();
    }
    double score = match.get().score();
    boolean matched = threshold.test(score);
    getLogger().fine(() -> "ip=" + ip.get() + " reputation score=" + score + " (list=" + match.get().listId() + ") against "
        + threshold + " -> " + (matched ? "match" : "no match"));
    return matched ? matched(match.get().listId(), "score=" + score) : notMatched();
  }
}
