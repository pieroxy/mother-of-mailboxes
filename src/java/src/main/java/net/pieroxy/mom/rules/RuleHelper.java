package net.pieroxy.mom.rules;

import net.pieroxy.mom.utils.MailTools;
import net.pieroxy.mom.utils.logging.DecisionLog;
import net.pieroxy.mom.utils.logging.StatsLog;

import javax.mail.Message;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Walks an ordered list of {@link RuleInterface}s against a message. */
public class RuleHelper {
  private RuleHelper() {}

  /**
   * Core loop, reusable both at the top level (a whole account's rules) and inside a group like
   * {@link LearnedRulesGroupRule} (a sub-list of learned rules): applies rules in order until
   * one stops evaluation (a rule that matched without {@code keepProcessing}), or the list is
   * exhausted. A rule that throws doesn't block the following ones either.
   */
  public static RuleExecutionResult evaluate(List<RuleInterface> rules, Message message, Logger logger, String logContext) {
    return evaluate(rules, message, logger, logContext, null, false);
  }

  /**
   * Same as {@link #evaluate(List, Message, Logger, String)}, but also appends one
   * {@link DecisionLog.RuleOutcome} per rule actually reached to {@code outcomesOut} (when
   * non-null), each tagged {@code learnedRule} — used by {@link #processRules} (the true
   * top-level entry point) and, recursively, by {@link LearnedRulesGroupRule#applyRecordingOutcomes}
   * for its own sub-list, so a {@code LEARNED_RULES} group reached from the top level contributes
   * one outcome per *individual* learned rule, not one opaque entry for the whole group. Package-
   * visible rather than private for exactly that recursive call; nothing outside this package
   * needs it.
   */
  static RuleExecutionResult evaluate(List<RuleInterface> rules, Message message, Logger logger, String logContext,
                                       List<DecisionLog.RuleOutcome> outcomesOut, boolean learnedRule) {
    boolean anyMatched = false;
    boolean learnedRulesExecuted = false;
    boolean anyNonNoopActionApplied = false;
    for (RuleInterface rule : rules) {
      try {
        RuleExecutionResult result;
        if (outcomesOut != null && rule instanceof LearnedRulesGroupRule learnedRulesGroup) {
          // Recurse instead of treating the whole group as one opaque rule: each individual
          // learned rule it holds gets its own outcome, tagged learnedRule=true, directly in
          // outcomesOut — see the class javadoc on DecisionLog.RuleOutcome.
          result = learnedRulesGroup.applyRecordingOutcomes(message, outcomesOut);
        } else {
          result = rule.apply(message);
          if (outcomesOut != null) outcomesOut.add(toOutcome(rule, result, learnedRule));
        }
        learnedRulesExecuted |= result.learnedRulesExecuted();
        if (result.ruleApplied()) {
          anyMatched = true;
          // Accumulated across every rule that applied in this loop, not just the one that
          // ultimately blocks (if any): a keepProcessing rule earlier in the list can already
          // have run a real action before a later rule stops evaluation, or before the list
          // simply runs out.
          anyNonNoopActionApplied |= result.nonNoopActionApplied();
          if (!result.keepProcessing()) {
            return new RuleExecutionResult(true, false, learnedRulesExecuted, result.matchedDescription(), anyNonNoopActionApplied, null);
          }
        }
      } catch (Exception e) {
        if (outcomesOut != null) outcomesOut.add(new DecisionLog.RuleOutcome(rule.describe(), learnedRule, null, null, e.toString()));
        logger.log(Level.WARNING, "Rule failed on " + logContext + " for message from " + MailTools.describeFromSafely(message), e);
      }
    }
    return new RuleExecutionResult(anyMatched, true, learnedRulesExecuted, null, anyNonNoopActionApplied, null);
  }

  /**
   * A matched rule (or crashed-before-matching rule) as a decision-log entry — see
   * {@link DecisionLog.RuleOutcome}'s own javadoc for the exact matched/exception semantics this
   * implements. Package-visible: also used by {@link LearnedRulesGroupRule} for its fallback case.
   */
  static DecisionLog.RuleOutcome toOutcome(RuleInterface rule, RuleExecutionResult result, boolean learnedRule) {
    Boolean matched = (!result.ruleApplied() && result.exception() != null) ? null : result.ruleApplied();
    Boolean keepProcessing = result.ruleApplied() ? result.keepProcessing() : null;
    return new DecisionLog.RuleOutcome(rule.describe(), learnedRule, matched, keepProcessing, result.exception());
  }

  /**
   * Top-level entry point (shared by INBOX processing and manual replay from
   * mom-rules/ToProcess): runs rules via {@link #evaluate}, then, only if evaluation reached the
   * natural end of the list (no blocking match occurred) and the learned rules never got a
   * chance to run along the way, falls back to running learnedRulesFallback now. This is what
   * keeps every {@code config.json} that doesn't place a {@code LEARNED_RULES} entry explicitly
   * behaving exactly as it did before that type existed — learned rules still always run, just
   * implicitly, after everything else.
   * <p>
   * Also records exactly one {@code PROCESSED} stats event (see {@link StatsLog}) for the whole
   * call: {@code result=MATCH} (naming which rule, manual or learned, ultimately blocked the
   * message) if one did, {@code result=PASS} if the chain ran to completion without ever
   * blocking — regardless of whether a {@code keepProcessing} rule matched along the way (that
   * match has its own {@code MATCH} entry, logged separately by {@code Rule#apply}) — and exactly
   * one {@link DecisionLog} entry (see {@code trigger}), naming every rule actually reached and
   * what it decided.
   * @return whether at least one rule matched, and whether any of them ran a real (non-{@code NOOP}) action.
   */
  public static ProcessOutcome processRules(List<RuleInterface> rules, RuleInterface learnedRulesFallback, Message message, Logger logger,
                                             String logContext, RuleContext context, DecisionLog.Trigger trigger) {
    long start = System.nanoTime();
    String subject = MailTools.describeSubjectSafely(message);
    String from = MailTools.describeFromSafely(message);
    List<DecisionLog.RuleOutcome> outcomes = new ArrayList<>();

    boolean matched;
    boolean blocked;
    boolean nonNoopActionApplied;
    String matchedDescription = null;

    RuleExecutionResult result = evaluate(rules, message, logger, logContext, outcomes, false);
    if (!result.keepProcessing()) {
      matched = true;
      blocked = true; // a blocking rule already matched: never touch the learned rules afterward
      matchedDescription = result.matchedDescription();
      nonNoopActionApplied = result.nonNoopActionApplied();
    } else if (!result.learnedRulesExecuted()) {
      RuleExecutionResult fallbackResult = (learnedRulesFallback instanceof LearnedRulesGroupRule learnedRulesGroup)
          ? learnedRulesGroup.applyRecordingOutcomes(message, outcomes)
          : recordingOutcome(learnedRulesFallback, message, outcomes);
      // Bitwise |, not ||: the fallback must run even if a keepProcessing rule already matched
      // above (result.ruleApplied() true doesn't mean we can skip it, unlike the early return).
      matched = result.ruleApplied() | fallbackResult.ruleApplied();
      blocked = !fallbackResult.keepProcessing();
      if (blocked) matchedDescription = fallbackResult.matchedDescription();
      nonNoopActionApplied = result.nonNoopActionApplied() | fallbackResult.nonNoopActionApplied();
    } else {
      matched = result.ruleApplied();
      blocked = false;
      nonNoopActionApplied = result.nonNoopActionApplied();
    }

    long processedMs = (System.nanoTime() - start) / 1_000_000;
    StatsLog.recordProcessed(context.statsDir(), blocked ? StatsLog.ProcessResult.MATCH : StatsLog.ProcessResult.PASS, matchedDescription, processedMs);
    DecisionLog.recordRuleEvaluation(context.statsDir(), context.decisionLogRetentionDays(), trigger, subject, from, outcomes);
    return new ProcessOutcome(matched, nonNoopActionApplied);
  }

  /** {@code rule.apply(message)}, recording its outcome — for a fallback that isn't a real {@link LearnedRulesGroupRule} (e.g. a test double). */
  private static RuleExecutionResult recordingOutcome(RuleInterface rule, Message message, List<DecisionLog.RuleOutcome> outcomesOut) {
    RuleExecutionResult result = rule.apply(message);
    outcomesOut.add(toOutcome(rule, result, false));
    return result;
  }

  /**
   * What a whole message's pass through {@link #processRules} decided, for the caller to act on
   * (see {@code MailAccount#inspect}, which uses {@code nonNoopActionApplied} to drive its live
   * per-session counters).
   * @param matched              true if at least one rule matched (in rules or the fallback).
   * @param nonNoopActionApplied true if any rule that matched ran an action other than {@code NOOP}.
   */
  public record ProcessOutcome(boolean matched, boolean nonNoopActionApplied) {}
}
