package net.pieroxy.mom.config.general;

import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.rules.actions.ActionType;

import java.util.List;

/** Read directly by {@code AccountConfigApi} to show an account's rules — see {@link TypeScriptType}. */
@TypeScriptType
public class MailFilterRuleActionConfiguration {
  private ActionType type;
  private String key;
  /** Only for REPLY: the reply's plain-text body. */
  private String body;
  /**
   * Only set for composite actions (AND / OR).
   */
  private List<MailFilterRuleActionConfiguration> children;
  /**
   * Log level for this node (DEBUG/INFO/WARNING/ERROR), optional. Default: INFO.
   */
  private String logLevel;
  /**
   * Shown in the decision log; absent, a name is generated from type and key (see
   * {@code ActionNames}). Only read on a rule's top-level action.
   */
  private String name;
  /**
   * Whether this action does spam filtering rather than filing (display only). A {@code Boolean}
   * so that an unset flag isn't written out to config.json. Only read on a rule's top-level action.
   */
  private Boolean spam;

  public ActionType getType() {
    return type;
  }

  public void setType(ActionType type) {
    this.type = type;
  }

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public String getBody() {
    return body;
  }

  public void setBody(String body) {
    this.body = body;
  }

  public List<MailFilterRuleActionConfiguration> getChildren() {
    return children;
  }

  public void setChildren(List<MailFilterRuleActionConfiguration> children) {
    this.children = children;
  }

  public String getLogLevel() {
    return logLevel;
  }

  public void setLogLevel(String logLevel) {
    this.logLevel = logLevel;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public boolean isSpam() {
    return Boolean.TRUE.equals(spam);
  }

  public void setSpam(boolean spam) {
    this.spam = spam ? Boolean.TRUE : null;
  }
}
