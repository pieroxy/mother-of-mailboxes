package net.pieroxy.mom.rules;

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
 *                             message (see {@code net.pieroxy.mom.utils.logging.StatsLog}).
 * @param nonNoopActionApplied only meaningful when ruleApplied is true: whether the action that
 *                             ran was anything other than the literal {@code NOOP} type (see
 *                             {@code ActionType}) — lets {@link RuleHelper#processRules} tell
 *                             {@code MailAccount} whether this message actually got acted upon,
 *                             for the live per-account counters (see {@code AccountsApi}).
 * @param exception            {@code toString()} of whatever the matcher or the action threw (see
 *                             {@code Rule#apply}, which catches both internally rather than
 *                             letting either abort the rule chain) — null on a clean run, whether
 *                             or not it matched. Bubbled up so {@code DecisionLog} can show a rule
 *                             that errored as a distinct outcome from one that simply didn't
 *                             match (see {@code RuleHelper}'s per-rule decision-log entries).
 */
public record RuleExecutionResult(boolean ruleApplied, boolean keepProcessing, boolean learnedRulesExecuted,
                                   String matchedDescription, boolean nonNoopActionApplied, String exception) {
  /** No match: keepProcessing is irrelevant here (nothing to keep processing from), left true by convention. */
  public static final RuleExecutionResult NOT_APPLIED = new RuleExecutionResult(false, true, false, null, false, null);

  public static RuleExecutionResult applied(boolean keepProcessing, String matchedDescription, boolean nonNoopActionApplied) {
    return new RuleExecutionResult(true, keepProcessing, false, matchedDescription, nonNoopActionApplied, null);
  }

  /** Like {@link #applied}, but the action threw after a clean match — see {@code exception} above. */
  public static RuleExecutionResult applied(boolean keepProcessing, String matchedDescription, boolean nonNoopActionApplied, String exception) {
    return new RuleExecutionResult(true, keepProcessing, false, matchedDescription, nonNoopActionApplied, exception);
  }

  /** Like {@link #NOT_APPLIED}, but the matcher threw before a match could even be determined. */
  public static RuleExecutionResult notApplied(String exception) {
    return new RuleExecutionResult(false, true, false, null, false, exception);
  }
}
