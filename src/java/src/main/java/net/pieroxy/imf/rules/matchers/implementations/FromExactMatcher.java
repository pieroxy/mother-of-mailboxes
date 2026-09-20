package net.pieroxy.imf.rules.matchers.implementations;

import net.pieroxy.imf.config.general.MailFilterRuleMatcherConfiguration;
import net.pieroxy.imf.rules.matchers.MatchResult;
import net.pieroxy.imf.rules.matchers.Matcher;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import java.util.Arrays;
import java.util.Optional;

public class FromExactMatcher extends Matcher {
  @Override
  public void setConfig(MailFilterRuleMatcherConfiguration config) {
    super.setConfig(config);
    initLookupSet(false);
  }

  @Override
  public MatchResult matches(Message message) throws MessagingException {
    var froms = message.getFrom();
    if (froms == null) {
      getLogger().fine(() -> "no From header on message, no match against " + describeKey());
      return notMatched();
    }
    if (froms.length == 1) {
      Optional<String> hit = matchesKey(froms[0].toString(), false);
      getLogger().fine(() -> "tested from=" + froms[0] + " against " + describeKey()
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
      throw new MessagingException("Cannot learn a FROM_EQUALS rule: message must have exactly one From address");
    }
    return froms[0].toString();
  }
}
