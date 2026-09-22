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
 * Like {@link FromAddressMatcher}, but only compares the address's domain name
 * (e.g. "hotmail.com" for "jdupont@hotmail.com"), case-insensitively — useful for blocking a
 * whole domain rather than one specific address.
 */
public class FromDomainMatcher extends Matcher {
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
      String domain = extractDomain(froms[0]);
      Optional<String> hit = matchesKey(domain, true);
      getLogger().fine(() -> "tested from domain=" + domain + " against " + describeKey()
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
      throw new MessagingException("Cannot learn a FROM_DOMAIN_EQUALS rule: message must have exactly one From address");
    }
    String domain = extractDomain(froms[0]);
    if (domain == null) {
      throw new MessagingException("Cannot learn a FROM_DOMAIN_EQUALS rule: From address has no domain part");
    }
    return domain;
  }

  private static String extractDomain(Address address) {
    String raw = address instanceof InternetAddress ? ((InternetAddress) address).getAddress() : address.toString();
    if (raw == null) return null;
    int at = raw.lastIndexOf('@');
    return at >= 0 && at < raw.length() - 1 ? raw.substring(at + 1) : null;
  }
}
