package net.pieroxy.mom.rules.matchers.implementations;

import net.pieroxy.mom.config.general.MailFilterRuleMatcherConfiguration;
import net.pieroxy.mom.rules.matchers.MatchResult;
import net.pieroxy.mom.rules.matchers.Matcher;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.MimeUtility;
import java.util.Optional;

/**
 * Matches the mailing list identifier of the {@code List-Id} header (RFC 2919), case-insensitively:
 * the part between angle brackets, so that the list's display name can change freely, or the
 * whole trimmed value when there are no brackets. Only the topmost occurrence is read: it is the
 * list that delivered the message. Keys are parsed the same way, so a pasted header value works.
 */
public class ListIdMatcher extends Matcher {
  @Override
  public void setConfig(MailFilterRuleMatcherConfiguration config) {
    super.setConfig(config);
    initLookupSet(true, ListIdMatcher::listIdOf);
  }

  @Override
  public MatchResult matches(Message message) throws MessagingException {
    String listId = listIdOf(message);
    Optional<String> hit = matchesKey(listId, true);
    getLogger().fine(() -> "tested List-Id=" + listId + " against " + describeKey()
            + " -> " + (hit.isPresent() ? "match" : "no match"));
    return hit.map(this::matched).orElseGet(this::notMatched);
  }

  @Override
  public String extractKeyFromExample(Message message) throws MessagingException {
    String listId = listIdOf(message);
    if (listId == null) {
      throw new MessagingException("Cannot learn a LIST_ID_EQUALS rule: message has no usable List-Id header");
    }
    return listId;
  }

  private static String listIdOf(Message message) throws MessagingException {
    String[] values = message.getHeader("List-Id");
    return values == null || values.length == 0 ? null : listIdOf(values[0]);
  }

  /** Lower-cased; null when empty. */
  static String listIdOf(String headerValue) {
    String value = MimeUtility.unfold(headerValue).trim();
    int open = value.lastIndexOf('<');
    int close = value.lastIndexOf('>');
    if (open >= 0 && close > open) value = value.substring(open + 1, close).trim();
    return value.isEmpty() ? null : value.toLowerCase();
  }
}
