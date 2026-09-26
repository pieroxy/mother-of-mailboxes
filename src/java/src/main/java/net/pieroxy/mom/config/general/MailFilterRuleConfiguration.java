package net.pieroxy.mom.config.general;

import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.rules.RuleType;

/** Read directly by {@code AccountConfigApi} to show an account's rules — see {@link TypeScriptType}. */
@TypeScriptType
public class MailFilterRuleConfiguration {
  /**
   * Optional: defaults to MATCHER_ACTION_RULE when absent, so every config.json written before
   * this field existed keeps working unchanged. See RuleType and RuleCatalog#build.
   */
  private RuleType type;
  private MailFilterRuleMatcherConfiguration matcher;
  private MailFilterRuleActionConfiguration action;
  /**
   * By default (false), the first rule that matches stops evaluation for this message — see
   * RuleHelper.processRules(). When true, its action still runs, but evaluation continues on to
   * the following rules as if this one hadn't matched: useful for a rule that only
   * observes/logs (e.g. comparing a new classifier against the old one) without ever blocking
   * the real rules that would otherwise apply afterward.
   */
  private boolean keepProcessing;

  public RuleType getType() {
    return type;
  }

  public void setType(RuleType type) {
    this.type = type;
  }

  public MailFilterRuleMatcherConfiguration getMatcher() {
    return matcher;
  }

  public void setMatcher(MailFilterRuleMatcherConfiguration matcher) {
    this.matcher = matcher;
  }

  public MailFilterRuleActionConfiguration getAction() {
    return action;
  }

  public void setAction(MailFilterRuleActionConfiguration action) {
    this.action = action;
  }

  public boolean isKeepProcessing() {
    return keepProcessing;
  }

  public void setKeepProcessing(boolean keepProcessing) {
    this.keepProcessing = keepProcessing;
  }
}
