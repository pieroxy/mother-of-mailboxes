package net.pieroxy.imf.rules;

import net.pieroxy.imf.config.MailFilterRuleConfiguration;
import net.pieroxy.imf.utils.logging.StatsLog;
import net.pieroxy.imf.rules.actions.Action;
import net.pieroxy.imf.rules.matchers.MatchResult;
import net.pieroxy.imf.rules.matchers.Matcher;
import net.pieroxy.imf.utils.MailTools;

import javax.mail.Message;
import java.util.logging.Level;

public class Rule implements RuleInterface {

  private final MailFilterRuleConfiguration config;
  private final Matcher matcher;
  private final Action action;
  private final RuleContext context;

  /** Equivalent to {@link #Rule(MailFilterRuleConfiguration, RuleContext)} with no account context available. */
  public Rule(MailFilterRuleConfiguration config) {
    this(config, RuleContext.EMPTY);
  }

  public Rule(MailFilterRuleConfiguration config, RuleContext context) {
    if (config.getMatcher() == null || config.getAction() == null) {
      throw new IllegalStateException("A MATCHER_ACTION_RULE rule needs both a matcher and an action");
    }
    this.config = config;
    this.context = context;
    matcher = Matcher.build(config.getMatcher(), context);
    action = Action.build(config.getAction(), context);
  }

  /**
   * Compact representation for the startup logs, e.g.
   * {@code Rule(FROM_DOMAIN_EQUALS(toto.com),MOVE_TO(Work))}, or
   * {@code Rule(...,...) [keepProcessing]} if the rule lets evaluation carry on to the following
   * rules even when it matches.
   */
  @Override
  public String describe() {
    String base = "Rule(" + matcher.describe() + "," + action.describe() + ")";
    return config.isKeepProcessing() ? base + " [keepProcessing]" : base;
  }

  /**
   * Logs on each node's own logger (matcher/action): WARNING on error, INFO when the matcher
   * matches and when the action runs.
   */
  @Override
  public RuleExecutionResult apply(Message message) {
    MatchResult matchResult;
    try {
      matchResult = matcher.matches(message);
    } catch (Exception e) {
      matcher.getLogger().log(Level.WARNING, "Matcher failed on message from " + MailTools.describeFromSafely(message), e);
      return RuleExecutionResult.NOT_APPLIED;
    }
    if (!matchResult.matched()) {
      return RuleExecutionResult.NOT_APPLIED;
    }
    matcher.getLogger().info(() -> matchResult.debugString() + " matched message from " + MailTools.describeFromSafely(message));
    StatsLog.recordMatch(context.statsDir(), matchResult.debugString());

    try {
      boolean result = action.run(message);
      action.getLogger().info(() -> "Action "+action.describe()+" applied (success=" + result + ") to message from " + MailTools.describeFromSafely(message));
    } catch (Exception e) {
      action.getLogger().log(Level.WARNING, "Action failed on message from " + MailTools.describeFromSafely(message), e);
    }
    return RuleExecutionResult.applied(config.isKeepProcessing());
  }
}
