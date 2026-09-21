package net.pieroxy.imf.rules;

/**
 * What a single {@link RuleInterface#apply} call decided, for {@link RuleHelper} to act on. A
 * fresh instance every call — never mutated in place — so a rule implementation that forgets to
 * set one of these fields can't silently inherit a stale value left over from whichever rule ran
 * right before it in the same loop.
 *
 * @param ruleApplied          true if this rule matched (and ran its action).
 * @param keepProcessing       only meaningful when ruleApplied is true: whether evaluation should
 *                             carry on to the next rule (see {@code keepProcessing} in
 *                             {@code MailFilterRuleConfiguration}) instead of stopping here.
 * @param learnedRulesExecuted true if, as part of this call, the account's learned rules
 *                             actually ran (see {@link LearnedRulesGroupRule}) — lets
 *                             {@link RuleHelper} know it doesn't need to run them again as a
 *                             fallback once the whole list has been walked.
 * @param matchedDescription   only meaningful when ruleApplied is true: the matched
 *                             {@code MatchResult}'s debug string (see {@code Rule#apply}) —
 *                             bubbled up so {@link RuleHelper#processRules} can name, on its
 *                             {@code PROCESSED} stats event, which rule ultimately blocked the
 *                             message (see {@code net.pieroxy.imf.utils.logging.StatsLog}).
 */
public record RuleExecutionResult(boolean ruleApplied, boolean keepProcessing, boolean learnedRulesExecuted, String matchedDescription) {
  /** No match: keepProcessing is irrelevant here (nothing to keep processing from), left true by convention. */
  public static final RuleExecutionResult NOT_APPLIED = new RuleExecutionResult(false, true, false, null);

  public static RuleExecutionResult applied(boolean keepProcessing, String matchedDescription) {
    return new RuleExecutionResult(true, keepProcessing, false, matchedDescription);
  }
}
