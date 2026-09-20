package net.pieroxy.imf.rules;

import net.pieroxy.imf.utils.logging.StatsLog;

import java.io.File;

/**
 * Whatever a matcher or action might need to know about the account it's being built for, at
 * rule-build time — the rule's own config (see {@code MailFilterRuleMatcherConfiguration}/
 * {@code MailFilterRuleActionConfiguration}) knows nothing about the account it happens to run
 * under, so this is the other half. Currently the classifier model files (used by
 * {@code SubjectClassifierMatcher}/{@code HeaderClassifierMatcher}/{@code BodyClassifierMatcher},
 * replacing what used to be a per-thread ThreadLocal) and the stats log directory (see
 * {@code Rule#apply}, {@link StatsLog}); expected to grow further — e.g.
 * the account's own address, once REPLY/FORWARD actions exist and need it.
 */
public record RuleContext(File subjectModelFile, File headerModelFile, File bodyModelFile, File statsDir) {
  /** No account-specific info available — matches how things behaved before this existed. */
  public static final RuleContext EMPTY = new RuleContext(null, null, null, null);
}
