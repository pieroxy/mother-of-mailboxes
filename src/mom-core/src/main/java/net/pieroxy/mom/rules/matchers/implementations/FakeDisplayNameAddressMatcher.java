package net.pieroxy.mom.rules.matchers.implementations;

import net.pieroxy.mom.detection.from.DisplayNameAddress;
import net.pieroxy.mom.rules.matchers.MatchResult;
import net.pieroxy.mom.rules.matchers.Matcher;

import javax.mail.Message;
import javax.mail.MessagingException;
import java.util.Optional;

/**
 * Compares the {@link DisplayNameAddress} of the message's {@code From} header ({@code none},
 * {@code same}, {@code same_domain}, {@code other_domain}) against the configured key,
 * case-insensitively.
 */
public class FakeDisplayNameAddressMatcher extends Matcher {
  @Override
  public MatchResult matches(Message message) throws MessagingException {
    String displayNameAddress = DisplayNameAddress.of(message).getCode();
    Optional<String> hit = matchingKey(displayNameAddress, String::equalsIgnoreCase);
    getLogger().fine(() -> "tested display name address=" + displayNameAddress + " against " + describeKey()
            + " -> " + (hit.isPresent() ? "match" : "no match"));
    return hit.map(this::matched).orElseGet(this::notMatched);
  }
}
