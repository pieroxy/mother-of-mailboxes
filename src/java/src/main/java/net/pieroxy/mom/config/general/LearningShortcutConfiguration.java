package net.pieroxy.mom.config.general;

/**
 * One entry of {@code learningShortcuts} (see {@link MailAccountConfiguration}): a single flat
 * folder directly under {@code mom-rules/} — e.g. {@code mom-rules/MoveSameDomainToSpam} —
 * bound to one fixed (matcher type, action) pair, instead of the full
 * {@code mom-rules/<MATCHER_TYPE>/<ACTION_TYPE>/<key>} discovery tree (see
 * {@link net.pieroxy.mom.learning.RuleLearner}). The discovery tree stays useful to see what's
 * possible, but subscribing to every matcher/action combination in an IMAP client just to use a
 * handful of them in practice doesn't scale — a shortcut is the one folder worth actually
 * subscribing to for a specific, already-decided (matcher, action) combination.
 * <p>
 * {@code matcher} only ever needs {@code type} (its {@code key} is extracted per example, same
 * as in the discovery tree — setting one here is rejected, see
 * {@link net.pieroxy.mom.learning.RuleLearner}'s validation); {@code action} is fully fixed
 * (including its {@code key}, e.g. the destination folder for {@code MOVE_TO}) since there's no
 * folder level left to carry it.
 */
public class LearningShortcutConfiguration {
  private String name;
  private MailFilterRuleMatcherConfiguration matcher;
  private MailFilterRuleActionConfiguration action;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
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
}
