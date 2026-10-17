package net.pieroxy.mom.rules.matchers.implementations;

import net.pieroxy.mom.detection.lookalike.LookalikeText;
import net.pieroxy.mom.rules.matchers.MatchResult;
import net.pieroxy.mom.rules.matchers.Matcher;

import javax.mail.Message;
import javax.mail.MessagingException;
import java.util.Optional;

/**
 * Matches when the subject or the sender's display name holds a word of one of the configured
 * {@link LookalikeText} kinds ({@code mixed_script}, {@code whole_script}, {@code fancy_letters}),
 * case-insensitively. The match detail names the first such word and where it was found.
 */
public class LookalikeTextMatcher extends Matcher {
  @Override
  public MatchResult matches(Message message) throws MessagingException {
    for (LookalikeText.Finding finding : LookalikeText.findIn(message)) {
      Optional<String> hit = matchingKey(finding.kind().getCode(), String::equalsIgnoreCase);
      if (hit.isPresent()) {
        getLogger().fine(() -> "found " + finding.kind().getCode() + " in " + finding.where() + ": " + finding.detail());
        return matched(finding.where() + ": " + finding.detail());
      }
    }
    getLogger().fine(() -> "no lookalike text matching " + describeKey());
    return notMatched();
  }
}
