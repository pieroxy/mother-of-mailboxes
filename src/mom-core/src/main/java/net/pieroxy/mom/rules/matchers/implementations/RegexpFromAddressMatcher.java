package net.pieroxy.mom.rules.matchers.implementations;

import net.pieroxy.mom.rules.matchers.MatchResult;
import net.pieroxy.mom.rules.matchers.Matcher;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Like {@link FromAddressMatcher}, but the configured key(s) are regular expressions tested
 * against the sender's email address (just "local@domain", ignoring any display name) via a full
 * match (like {@link String#matches}), e.g. {@code ".*@(sales|support)\\.example\\.com"}.
 * <p>
 * Not learnable: unlike a literal address or domain, there's no sensible way to generalize a
 * regular expression from one example message — see
 * {@link net.pieroxy.mom.rules.matchers.MatcherType}.
 */
public class RegexpFromAddressMatcher extends Matcher {
  private List<Pattern> patterns;

  @Override
  protected void validate() {
    List<String> keys = getConfig().getKeys() != null
        ? new ArrayList<>(getConfig().getKeys())
        : (getConfig().getKey() != null ? List.of(getConfig().getKey()) : List.of());
    if (keys.isEmpty()) {
      throw new IllegalArgumentException("FROM_ADDRESS_REGEXP requires \"key\" or \"keys\"");
    }
    // Compiled once at startup, not per message: fails loudly right away on a typo'd regex
    // instead of repeatedly on every message inspected, and avoids recompiling on every match.
    patterns = new ArrayList<>(keys.size());
    for (String key : keys) {
      try {
        patterns.add(Pattern.compile(key));
      } catch (PatternSyntaxException e) {
        throw new IllegalArgumentException("FROM_ADDRESS_REGEXP: invalid regular expression \"" + key + "\"", e);
      }
    }
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
      boolean hit = address != null && patterns.stream().anyMatch(p -> p.matcher(address).matches());
      getLogger().fine(() -> "tested from address=" + address + " against " + describeKey()
              + " -> " + (hit ? "match" : "no match"));
      return hit ? matched(address) : notMatched();
    }
    getLogger().fine(() -> "multiple From addresses " + Arrays.toString(froms) + ", no match against " + describeKey());
    return notMatched();
  }

  private static String extractAddress(Address address) {
    return address instanceof InternetAddress ? ((InternetAddress) address).getAddress() : address.toString();
  }
}
