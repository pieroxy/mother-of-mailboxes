package net.pieroxy.mom.rules.matchers.implementations;

import net.pieroxy.mom.rules.matchers.MatchResult;
import net.pieroxy.mom.rules.matchers.Matcher;

import javax.mail.Message;
import javax.mail.MessagingException;

/**
 * Matches if its single child matcher does not. Unlike AND/OR, a child count other than exactly
 * one is never meaningful (negating nothing, or negating several conditions at once with no way
 * to say which), so that's rejected once at startup — see {@link #validate()} — rather than
 * risking it being silently misread as, say, "negate only the first child".
 */
public class NotMatcher extends Matcher {
  @Override
  public MatchResult matches(Message message) throws MessagingException {
    Matcher child = getChildren().get(0);
    MatchResult result = child.matches(message);
    getLogger().fine(() -> "NOT: child " + child.getClass().getSimpleName() + " -> " + result.matched());
    // NOT matches exactly when the child doesn't — result.debugString() is null in that case
    // (nothing to explain about a non-match, see MatchResult), so describe() (the child's
    // config-level description, not a per-message detail) is what's shown instead.
    return result.matched() ? notMatched() : matched(child.describe());
  }

  @Override
  protected void validate() {
    if (getChildren().size() != 1) {
      throw new IllegalArgumentException("NOT requires exactly one child matcher, got " + getChildren().size());
    }
  }
}
