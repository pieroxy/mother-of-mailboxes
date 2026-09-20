package net.pieroxy.imf.rules.matchers.implementations;

import net.pieroxy.imf.config.MailFilterRuleMatcherConfiguration;
import net.pieroxy.imf.detection.reputation.ReputationMatch;
import net.pieroxy.imf.detection.reputation.ReputationRegistry;
import net.pieroxy.imf.detection.reputation.ReputationRegistryHolder;
import net.pieroxy.imf.rules.matchers.MatchResult;
import net.pieroxy.imf.rules.matchers.Matcher;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import java.util.Optional;
import java.util.Set;

/**
 * Like {@link IpReputationMatcher}, but on the domain of the {@code From:} address rather than
 * the connecting IP, against {@code DOMAIN}-type lists.
 */
public class FromDomainReputationMatcher extends Matcher {
  private final ReputationRegistry registry;
  private Set<String> listIds;
  private ReputationThreshold threshold;

  public FromDomainReputationMatcher() {
    this(ReputationRegistryHolder.get());
  }

  /** Visible for tests: allows injecting a registry with no real download. */
  FromDomainReputationMatcher(ReputationRegistry registry) {
    this.registry = registry;
  }

  @Override
  public void setConfig(MailFilterRuleMatcherConfiguration config) {
    super.setConfig(config);
    listIds = config.getListIds();
    if (listIds == null || listIds.isEmpty()) {
      throw new IllegalArgumentException("FROM_DOMAIN_REPUTATION_EQUALS requires at least one listId");
    }
    threshold = ReputationThreshold.parse(config.getKey(), "FROM_DOMAIN_REPUTATION_EQUALS");
  }

  @Override
  protected String describeKey() {
    return getConfig().getKey() + " in " + listIds;
  }

  @Override
  public MatchResult matches(Message message) throws MessagingException {
    Address[] froms = message.getFrom();
    if (froms == null || froms.length != 1) {
      getLogger().fine(() -> "no single From address on message, no match against " + threshold);
      return notMatched();
    }
    String domain = extractDomain(froms[0]);
    if (domain == null) {
      getLogger().fine(() -> "From address has no domain part, no match against " + threshold);
      return notMatched();
    }
    Optional<ReputationMatch> match = registry.domainScore(domain, listIds);
    if (match.isEmpty()) {
      getLogger().fine(() -> "from domain=" + domain + " not present in any referenced reputation list");
      return notMatched();
    }
    double score = match.get().score();
    boolean matched = threshold.test(score);
    getLogger().fine(() -> "from domain=" + domain + " reputation score=" + score + " (list=" + match.get().listId()
        + ") against " + threshold + " -> " + (matched ? "match" : "no match"));
    return matched ? matched(match.get().listId(), "score=" + score) : notMatched();
  }

  private static String extractDomain(Address address) {
    String raw = address instanceof InternetAddress ? ((InternetAddress) address).getAddress() : address.toString();
    if (raw == null) return null;
    int at = raw.lastIndexOf('@');
    return at >= 0 && at < raw.length() - 1 ? raw.substring(at + 1) : null;
  }
}
