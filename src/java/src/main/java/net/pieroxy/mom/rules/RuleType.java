package net.pieroxy.mom.rules;

/**
 * Discriminates what a {@code rules} entry in {@code config.json} builds — see
 * {@link RuleCatalog#build}. Optional in the JSON: absent defaults to
 * {@code MATCHER_ACTION_RULE}, so every {@code config.json} written before this type existed
 * keeps working unchanged.
 */
public enum RuleType {
  /** The classic case: a {@code matcher} + an {@code action} (see {@link Rule}). */
  MATCHER_ACTION_RULE,
  /**
   * Runs the account's learned rules right at this position in the list (see
   * {@link LearnedRulesGroupRule}) instead of only ever at the very end. Must not carry a
   * {@code matcher} or an {@code action}. At most one such entry per account: see
   * {@link RuleCatalog#build}. If no entry of this type is present at all, the learned rules
   * still run — implicitly, after every other rule — exactly as before this type existed; see
   * {@link RuleHelper#processRules}.
   */
  LEARNED_RULES
}
