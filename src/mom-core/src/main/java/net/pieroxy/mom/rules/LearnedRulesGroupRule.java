package net.pieroxy.mom.rules;

import net.pieroxy.mom.learning.LearnedRulesStore;
import net.pieroxy.mom.utils.logging.DecisionLog;

import javax.mail.Message;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Wraps an account's learned rules (see {@link LearnedRulesStore}) as a single
 * {@link RuleInterface}, so the whole group can be slotted anywhere in {@code config.json}'s
 * {@code rules} list via a {@code RuleType.LEARNED_RULES} entry (see {@link RuleCatalog#build}) —
 * or, if the account's config never mentions one, run implicitly after every other rule, exactly
 * as learned rules always have (see {@link RuleHelper#processRules}). Loaded once at
 * construction time (rebuilt whenever {@link RuleCatalog} rebuilds), like every other rule in
 * the catalog.
 */
public class LearnedRulesGroupRule implements RuleInterface {
  private final static Logger LOGGER = Logger.getLogger(LearnedRulesGroupRule.class.getName());

  private final List<RuleInterface> learnedRules;

  public LearnedRulesGroupRule(LearnedRulesStore learnedRulesStore, RuleContext context) {
    this.learnedRules = learnedRulesStore.load().stream()
        .<RuleInterface>map(c -> new Rule(c, context))
        .collect(Collectors.toList());
  }

  @Override
  public String describe() {
    if (learnedRules.isEmpty()) return "LEARNED_RULES(none)";
    return "LEARNED_RULES(" + learnedRules.stream().map(RuleInterface::describe).collect(Collectors.joining(", ")) + ")";
  }

  @Override
  public RuleExecutionResult apply(Message message) {
    RuleExecutionResult result = RuleHelper.evaluate(learnedRules, message, LOGGER, "learned rules");
    return new RuleExecutionResult(result.ruleApplied(), result.keepProcessing(), true, result.matchedDescription(), result.nonNoopActionApplied(), result.exception());
  }

  /**
   * Same as {@link #apply}, but also appends one {@link DecisionLog.RuleOutcome} per individual
   * learned rule actually reached to {@code outcomesOut}, each tagged {@code learnedRule=true} —
   * so a {@code LEARNED_RULES} group contributes one decision-log line per *distinct* learned
   * rule it holds, not one opaque line for the whole group (see {@link RuleHelper#evaluate}).
   */
  public RuleExecutionResult applyRecordingOutcomes(Message message, List<DecisionLog.RuleOutcome> outcomesOut) {
    RuleExecutionResult result = RuleHelper.evaluate(learnedRules, message, LOGGER, "learned rules", outcomesOut, true);
    return new RuleExecutionResult(result.ruleApplied(), result.keepProcessing(), true, result.matchedDescription(), result.nonNoopActionApplied(), result.exception());
  }
}
