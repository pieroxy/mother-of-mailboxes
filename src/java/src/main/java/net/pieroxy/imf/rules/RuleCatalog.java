package net.pieroxy.imf.rules;

import net.pieroxy.imf.config.general.MailFilterRuleConfiguration;
import net.pieroxy.imf.learning.LearnedRulesStore;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Builds and caches the ordered list of {@link RuleInterface}s (manual config, with the account's
 * learned rules slotted in wherever a {@code LEARNED_RULES} entry appears — see
 * {@link RuleType}), to avoid rebuilding the Matcher/Action tree on every inspected message.
 * (Re)building only happens on first access, then after each call to {@link #invalidate()} —
 * typically when a new rule was just learned this cycle.
 */
public class RuleCatalog {
  private final List<MailFilterRuleConfiguration> manualRules;
  private final LearnedRulesStore learnedRulesStore;
  private final RuleContext context;
  private List<RuleInterface> rules;
  private LearnedRulesGroupRule learnedRulesFallback;

  /** Equivalent to {@link #RuleCatalog(List, LearnedRulesStore, RuleContext)} with no account context available. */
  public RuleCatalog(List<MailFilterRuleConfiguration> manualRules, LearnedRulesStore learnedRulesStore) {
    this(manualRules, learnedRulesStore, RuleContext.EMPTY);
  }

  public RuleCatalog(List<MailFilterRuleConfiguration> manualRules, LearnedRulesStore learnedRulesStore, RuleContext context) {
    this.manualRules = manualRules != null ? manualRules : List.of();
    this.learnedRulesStore = learnedRulesStore;
    this.context = context;
  }

  /** Builds on first call, then returns the same list until invalidate() is called. */
  public List<RuleInterface> get() {
    List<RuleInterface> current = rules;
    if (current == null) {
      current = build();
      rules = current;
    }
    return current;
  }

  /**
   * The account's learned rules as a single {@link RuleInterface}, always available regardless
   * of whether a {@code LEARNED_RULES} entry appears in {@code config.json} — see
   * {@link RuleHelper#processRules}, which uses it as the implicit fallback when {@code get()}'s
   * list doesn't already contain one (in which case this is that same instance).
   */
  public RuleInterface getLearnedRulesFallback() {
    get();
    return learnedRulesFallback;
  }

  /** Forces a rebuild (manual config + learned rules re-read from disk) on the next get(). */
  public void invalidate() {
    rules = null;
  }

  /**
   * Logs, in evaluation order (see {@link RuleHelper#evaluate}), one line per rule in the
   * catalog, plus the learned rules — shown last, marked implicit, if config.json didn't place
   * them explicitly. All in a single call to {@code logger.info} (a single
   * {@link java.util.logging.LogRecord}, hence a single call to {@code Handler.publish} —
   * synchronized on the JDK side) so the whole block is written as one unit and never interleaves
   * with another account logging in parallel on its own thread (see {@link MailAccount#run}).
   */
  public void logRules(Logger logger, String accountLabel) {
    List<RuleInterface> current = get();
    StringBuilder sb = new StringBuilder();
    sb.append("Rules for account ").append(accountLabel).append(':').append(System.lineSeparator());
    for (RuleInterface rule : current) {
      sb.append("  ").append(rule.describe()).append(System.lineSeparator());
    }
    if (!current.contains(learnedRulesFallback)) {
      sb.append("  ").append(learnedRulesFallback.describe()).append(" [implicit, runs after the rules above]").append(System.lineSeparator());
    }
    sb.setLength(sb.length() - System.lineSeparator().length()); // no trailing blank line
    logger.info(sb.toString());
  }

  private List<RuleInterface> build() {
    learnedRulesFallback = new LearnedRulesGroupRule(learnedRulesStore, context);
    List<RuleInterface> result = new ArrayList<>();
    boolean learnedRulesEntrySeen = false;
    for (MailFilterRuleConfiguration c : manualRules) {
      RuleType type = c.getType() != null ? c.getType() : RuleType.MATCHER_ACTION_RULE;
      if (type == RuleType.LEARNED_RULES) {
        if (c.getMatcher() != null || c.getAction() != null) {
          throw new IllegalStateException("A LEARNED_RULES rule must not have a matcher or an action");
        }
        if (learnedRulesEntrySeen) {
          throw new IllegalStateException("Only one LEARNED_RULES entry is allowed per account");
        }
        learnedRulesEntrySeen = true;
        result.add(learnedRulesFallback);
      } else {
        result.add(new Rule(c, context));
      }
    }
    return result;
  }
}
