package net.pieroxy.mom.config.general;

import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.rules.matchers.MatcherType;

import java.util.List;
import java.util.Set;

/** Read directly by {@code AccountConfigApi} to show an account's rules — see {@link TypeScriptType}. */
@TypeScriptType
public class MailFilterRuleMatcherConfiguration {
  private MatcherType type;
  private String key;
  /**
   * Alternative to key when several keys trigger the same action (e.g. N different addresses
   * all sent to Spam): avoids a whole rule being duplicated per key. A matcher uses whichever
   * of the two is non-null (keys takes priority if set).
   */
  private Set<String> keys;
  /**
   * Only set for composite matchers (AND / OR).
   */
  private List<MailFilterRuleMatcherConfiguration> children;
  /**
   * Log level for this node (DEBUG/INFO/WARNING/ERROR), optional. Default: WARNING.
   */
  private String logLevel;
  /**
   * Only for IP_REPUTATION_EQUALS / FROM_DOMAIN_REPUTATION_EQUALS: the "id"s (see
   * {@link Configuration#getReputationLists()}) of the reputation lists to check. If the
   * tested value is found in several of them, the score kept is the worst (max).
   */
  private Set<String> listIds;

  public MatcherType getType() {
    return type;
  }

  public void setType(MatcherType type) {
    this.type = type;
  }

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public Set<String> getKeys() {
    return keys;
  }

  public void setKeys(Set<String> keys) {
    this.keys = keys;
  }

  public List<MailFilterRuleMatcherConfiguration> getChildren() {
    return children;
  }

  public void setChildren(List<MailFilterRuleMatcherConfiguration> children) {
    this.children = children;
  }

  public String getLogLevel() {
    return logLevel;
  }

  public void setLogLevel(String logLevel) {
    this.logLevel = logLevel;
  }

  public Set<String> getListIds() {
    return listIds;
  }

  public void setListIds(Set<String> listIds) {
    this.listIds = listIds;
  }
}
