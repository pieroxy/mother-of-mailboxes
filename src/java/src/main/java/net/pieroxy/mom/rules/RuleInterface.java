package net.pieroxy.mom.rules;

import javax.mail.Message;

/**
 * Anything that can sit in the ordered list evaluated by {@link RuleHelper}: either a single
 * matcher/action pair ({@link Rule}) or a group that runs several of them as one unit (see
 * {@link LearnedRulesGroupRule}, which lets a whole account's learned rules be slotted in
 * anywhere among the manually-configured ones).
 */
public interface RuleInterface {
  /** Compact representation for the startup logs — see {@link RuleCatalog#logRules}. */
  String describe();

  /** Evaluates this rule (or group of rules) against message and reports what happened. */
  RuleExecutionResult apply(Message message);
}
