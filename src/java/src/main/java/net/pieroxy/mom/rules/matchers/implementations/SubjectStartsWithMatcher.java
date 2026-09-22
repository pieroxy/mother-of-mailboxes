package net.pieroxy.mom.rules.matchers.implementations;

import net.pieroxy.mom.rules.matchers.MatchResult;
import net.pieroxy.mom.rules.matchers.Matcher;

import javax.mail.Message;
import javax.mail.MessagingException;
import java.util.Optional;

/**
 * Matches if the message's {@code Subject:} starts with the configured key, case-insensitively.
 * <p>
 * Learnable, but naively: {@link #extractKeyFromExample} learns the **entire** subject of the
 * deposited example, not an intelligently-inferred prefix (there's no way to know which part of
 * the subject is the intended prefix vs. content specific to this particular example, e.g.
 * "Your invoice #12345" — should it learn "Your invoice" or the whole text?). For now, the
 * {@code <dataFolder>/<displayName>-learned-rules.json} file has to be hand-edited afterward to
 * shorten the learned key down to the actually-intended prefix.
 */
public class SubjectStartsWithMatcher extends Matcher {
  @Override
  public MatchResult matches(Message message) throws MessagingException {
    String subject = message.getSubject();
    if (subject == null) {
      getLogger().fine(() -> "no Subject header on message, no match against " + describeKey());
      return notMatched();
    }
    Optional<String> hit = matchingKey(subject, SubjectStartsWithMatcher::startsWithIgnoreCase);
    getLogger().fine(() -> "tested subject=" + subject + " against " + describeKey()
            + " -> " + (hit.isPresent() ? "match" : "no match"));
    return hit.map(this::matched).orElseGet(this::notMatched);
  }

  @Override
  public String extractKeyFromExample(Message message) throws MessagingException {
    String subject = message.getSubject();
    if (subject == null) {
      throw new MessagingException("Cannot learn a SUBJECT_STARTS_WITH rule: message has no Subject header");
    }
    return subject;
  }

  private static boolean startsWithIgnoreCase(String subject, String prefix) {
    return prefix != null && subject.length() >= prefix.length()
            && subject.regionMatches(true, 0, prefix, 0, prefix.length());
  }
}
