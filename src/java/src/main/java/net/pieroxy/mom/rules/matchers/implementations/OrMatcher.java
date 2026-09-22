package net.pieroxy.mom.rules.matchers.implementations;

import net.pieroxy.mom.rules.matchers.MatchResult;
import net.pieroxy.mom.rules.matchers.Matcher;

import javax.mail.Message;
import javax.mail.MessagingException;

/**
 * Matches if at least one of the child matchers matches (short-circuits on the first success).
 * With no children, an OR is false by convention.
 */
public class OrMatcher extends Matcher {
  @Override
  public MatchResult matches(Message message) throws MessagingException {
    for (Matcher child : getChildren()) {
      MatchResult result = child.matches(message);
      getLogger().fine(() -> "OR: child " + child.getClass().getSimpleName() + " -> " + result.matched());
      if (result.matched()) return matched(result.debugString());
    }
    return notMatched();
  }
}
