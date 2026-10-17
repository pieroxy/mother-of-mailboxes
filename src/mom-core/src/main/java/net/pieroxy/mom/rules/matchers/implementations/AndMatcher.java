package net.pieroxy.mom.rules.matchers.implementations;

import net.pieroxy.mom.rules.matchers.MatchResult;
import net.pieroxy.mom.rules.matchers.Matcher;

import javax.mail.Message;
import javax.mail.MessagingException;
import java.util.ArrayList;
import java.util.List;

/**
 * Matches if all the child matchers match (short-circuits on the first failure).
 * With no children, an AND is true by convention (vacuous truth).
 */
public class AndMatcher extends Matcher {
  @Override
  public MatchResult matches(Message message) throws MessagingException {
    List<String> details = new ArrayList<>();
    for (Matcher child : getChildren()) {
      MatchResult result = child.matches(message);
      getLogger().fine(() -> "AND: child " + child.getClass().getSimpleName() + " -> " + result.matched());
      if (!result.matched()) return notMatched();
      details.add(result.debugString());
    }
    return matched(String.join(", ", details));
  }
}
