package net.pieroxy.mom.rules.matchers.implementations;

import net.pieroxy.mom.detection.from.FromValidity;
import net.pieroxy.mom.rules.matchers.MatchResult;
import net.pieroxy.mom.rules.matchers.Matcher;

import javax.mail.Message;
import javax.mail.MessagingException;
import java.util.Optional;

/**
 * Compares the {@link FromValidity} of the message's {@code From} header ({@code valid},
 * {@code missing}, {@code multiple}, {@code invalid}) against the configured key,
 * case-insensitively.
 */
public class FromValidityMatcher extends Matcher {
  @Override
  public MatchResult matches(Message message) throws MessagingException {
    String validity = FromValidity.of(message).getCode();
    Optional<String> hit = matchingKey(validity, String::equalsIgnoreCase);
    getLogger().fine(() -> "tested from validity=" + validity + " against " + describeKey()
            + " -> " + (hit.isPresent() ? "match" : "no match"));
    return hit.map(this::matched).orElseGet(this::notMatched);
  }
}
