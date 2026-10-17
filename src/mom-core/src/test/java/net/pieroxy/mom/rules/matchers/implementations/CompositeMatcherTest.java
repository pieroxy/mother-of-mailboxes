package net.pieroxy.mom.rules.matchers.implementations;

import net.pieroxy.mom.config.general.MailFilterRuleMatcherConfiguration;
import net.pieroxy.mom.rules.matchers.Matcher;
import net.pieroxy.mom.rules.matchers.MatcherType;
import org.junit.Test;

import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Arrays;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Verifies the recursive construction (Matcher.build) and evaluation of composite AND/OR
 * matchers, built from real FROM_EQUALS matchers.
 */
public class CompositeMatcherTest {
  private final Session session = Session.getDefaultInstance(new Properties());

  private MimeMessage messageFrom(String address) throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress(address));
    return message;
  }

  private static MailFilterRuleMatcherConfiguration leaf(String email) {
    MailFilterRuleMatcherConfiguration c = new MailFilterRuleMatcherConfiguration();
    c.setType(MatcherType.FROM_EQUALS);
    c.setKey(email);
    return c;
  }

  private static MailFilterRuleMatcherConfiguration composite(MatcherType type, MailFilterRuleMatcherConfiguration... children) {
    MailFilterRuleMatcherConfiguration c = new MailFilterRuleMatcherConfiguration();
    c.setType(type);
    c.setChildren(Arrays.asList(children));
    return c;
  }

  @Test
  public void andMatchesOnlyWhenAllChildrenMatch() throws Exception {
    Matcher and = Matcher.build(composite(MatcherType.AND, leaf("alice@example.com"), leaf("alice@example.com")));
    assertTrue(and.matches(messageFrom("alice@example.com")).matched());

    Matcher andMismatch = Matcher.build(composite(MatcherType.AND, leaf("alice@example.com"), leaf("bob@example.com")));
    assertFalse(andMismatch.matches(messageFrom("alice@example.com")).matched());
  }

  @Test
  public void orMatchesWhenAnyChildMatches() throws Exception {
    Matcher or = Matcher.build(composite(MatcherType.OR, leaf("bob@example.com"), leaf("alice@example.com")));
    assertTrue(or.matches(messageFrom("alice@example.com")).matched());

    Matcher orMismatch = Matcher.build(composite(MatcherType.OR, leaf("bob@example.com"), leaf("carol@example.com")));
    assertFalse(orMismatch.matches(messageFrom("alice@example.com")).matched());
  }

  @Test
  public void nestedAndOrCompose() throws Exception {
    // (bob OR alice) AND alice
    Matcher rule = Matcher.build(composite(MatcherType.AND,
            composite(MatcherType.OR, leaf("bob@example.com"), leaf("alice@example.com")),
            leaf("alice@example.com")));

    assertTrue(rule.matches(messageFrom("alice@example.com")).matched());
    assertFalse(rule.matches(messageFrom("carol@example.com")).matched());
  }

  @Test
  public void emptyAndIsVacuouslyTrueEmptyOrIsFalse() throws Exception {
    assertTrue(new AndMatcher().matches(messageFrom("alice@example.com")).matched());
    assertFalse(new OrMatcher().matches(messageFrom("alice@example.com")).matched());
  }

  @Test
  public void andDebugStringListsEveryChildThatMatched() throws Exception {
    Matcher and = Matcher.build(composite(MatcherType.AND, leaf("alice@example.com"), leaf("alice@example.com")));

    assertEquals("AndMatcher(FromExactMatcher(alice@example.com), FromExactMatcher(alice@example.com))",
            and.matches(messageFrom("alice@example.com")).debugString());
  }

  @Test
  public void orDebugStringNamesOnlyTheChildThatMatched() throws Exception {
    // The first child (bob) doesn't match and so doesn't appear in the debug string: only the
    // one that actually made the OR match shows up there.
    Matcher or = Matcher.build(composite(MatcherType.OR, leaf("bob@example.com"), leaf("alice@example.com")));

    assertEquals("OrMatcher(FromExactMatcher(alice@example.com))",
            or.matches(messageFrom("alice@example.com")).debugString());
  }

  @Test
  public void notMatchesExactlyWhenItsChildDoesNot() throws Exception {
    Matcher not = Matcher.build(composite(MatcherType.NOT, leaf("alice@example.com")));

    assertFalse(not.matches(messageFrom("alice@example.com")).matched());
    assertTrue(not.matches(messageFrom("bob@example.com")).matched());
  }

  @Test
  public void notDebugStringDescribesTheNegatedChild() throws Exception {
    // The child didn't match (that's why NOT did) — MatchResult.debugString() is null for a
    // non-match, so there's nothing per-message to surface from it; describe() (the child's
    // config-level description) is what's shown instead.
    Matcher not = Matcher.build(composite(MatcherType.NOT, leaf("alice@example.com")));

    assertEquals("NotMatcher(FROM_EQUALS(alice@example.com))",
            not.matches(messageFrom("bob@example.com")).debugString());
  }

  @Test
  public void notWithZeroChildrenFailsFastAtBuildTime() {
    MailFilterRuleMatcherConfiguration config = composite(MatcherType.NOT);
    try {
      Matcher.build(config);
      fail("expected an IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      // NOT of nothing isn't meaningful — must be rejected at startup, not deferred to the first message.
    }
  }

  @Test
  public void notWithMultipleChildrenFailsFastAtBuildTime() {
    MailFilterRuleMatcherConfiguration config = composite(MatcherType.NOT, leaf("alice@example.com"), leaf("bob@example.com"));
    try {
      Matcher.build(config);
      fail("expected an IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      // Which of several children would NOT negate? Ambiguous, so rejected rather than guessed.
    }
  }

  @Test
  public void notCombinesWithAndOr() throws Exception {
    // alice AND NOT(bob) — matches alice, rejects a message claiming to be both alice and bob
    // (impossible for a real From header, but exercises the composition regardless).
    Matcher rule = Matcher.build(composite(MatcherType.AND,
            leaf("alice@example.com"),
            composite(MatcherType.NOT, leaf("bob@example.com"))));

    assertTrue(rule.matches(messageFrom("alice@example.com")).matched());
    assertFalse(rule.matches(messageFrom("bob@example.com")).matched());
  }
}
