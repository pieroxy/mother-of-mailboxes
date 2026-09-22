package net.pieroxy.mom.rules.matchers.implementations;

import net.pieroxy.mom.config.general.MailFilterRuleMatcherConfiguration;
import net.pieroxy.mom.rules.matchers.MatchResult;
import net.pieroxy.mom.rules.matchers.Matcher;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import java.util.Arrays;
import java.util.Optional;

/**
 * Like {@link FromExactMatcher}, but only compares the email address itself
 * (e.g. "jdupont@hotmail.com"), without the display name ("Jean Dupont <jdupont@hotmail.com>"),
 * case-insensitively.
 */
public class FromAddressMatcher extends Matcher {
  @Override
  public void setConfig(MailFilterRuleMatcherConfiguration config) {
    super.setConfig(config);
    initLookupSet(true);
  }

  @Override
  public MatchResult matches(Message message) throws MessagingException {
    var froms = message.getFrom();
    if (froms == null) {
      getLogger().fine(() -> "no From header on message, no match against " + describeKey());
      return notMatched();
    }
    if (froms.length == 1) {
      String address = extractAddress(froms[0]);
      Optional<String> hit = matchesKey(address, true);
      getLogger().fine(() -> "tested from address=" + address + " against " + describeKey()
              + " -> " + (hit.isPresent() ? "match" : "no match"));
      return hit.map(this::matched).orElseGet(this::notMatched);
    }
    getLogger().fine(() -> "multiple From addresses " + Arrays.toString(froms) + ", no match against " + describeKey());
    return notMatched();
  }

  @Override
  public String extractKeyFromExample(Message message) throws MessagingException {
    Address[] froms = message.getFrom();
    if (froms == null || froms.length != 1) {
      throw new MessagingException("Cannot learn a FROM_ADDRESS_EQUALS rule: message must have exactly one From address");
    }
    String address = extractAddress(froms[0]);
    if (address == null) {
      throw new MessagingException("Cannot learn a FROM_ADDRESS_EQUALS rule: From address has no email part");
    }
    return address;
  }

  private static String extractAddress(Address address) {
    return address instanceof InternetAddress ? ((InternetAddress) address).getAddress() : address.toString();
  }
}
