package net.pieroxy.imf.rules.matchers.implementations;

import net.pieroxy.imf.config.general.MailFilterRuleMatcherConfiguration;
import net.pieroxy.imf.rules.matchers.Matcher;
import net.pieroxy.imf.rules.matchers.MatcherType;
import org.junit.Test;

import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RegexpFromAddressMatcherTest {
  private final Session session = Session.getDefaultInstance(new Properties());

  private static MailFilterRuleMatcherConfiguration configWithKey(String key) {
    MailFilterRuleMatcherConfiguration config = new MailFilterRuleMatcherConfiguration();
    config.setType(MatcherType.FROM_ADDRESS_REGEXP);
    config.setKey(key);
    return config;
  }

  private Matcher matcherFor(String key) {
    return Matcher.build(configWithKey(key));
  }

  @Test
  public void matchesAddressAgainstRegexp() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("jdupont@hotmail.com", "Jean Dupont"));

    assertTrue(matcherFor(".*@hotmail\\.com").matches(message).matched());
  }

  @Test
  public void doesNotMatchWhenRegexpDoesNotFullyMatch() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("jdupont@hotmail.com"));

    // "hotmail" alone would match via find(), but this matcher does a full match.
    assertFalse(matcherFor("hotmail").matches(message).matched());
  }

  @Test
  public void doesNotMatchDifferentAddress() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("alice@example.com"));

    assertFalse(matcherFor(".*@other\\.com").matches(message).matched());
  }

  @Test
  public void doesNotMatchWhenNoFromHeader() throws Exception {
    MimeMessage message = new MimeMessage(session);

    assertFalse(matcherFor(".*@example\\.com").matches(message).matched());
  }

  @Test
  public void doesNotMatchWhenMultipleFromAddresses() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.addFrom(new InternetAddress[]{
            new InternetAddress("alice@example.com"),
            new InternetAddress("bob@example.com")
    });

    assertFalse(matcherFor(".*@example\\.com").matches(message).matched());
  }

  @Test
  public void matchesAnyKeyWhenKeysIsUsedInsteadOfKey() throws Exception {
    MailFilterRuleMatcherConfiguration config = new MailFilterRuleMatcherConfiguration();
    config.setType(MatcherType.FROM_ADDRESS_REGEXP);
    config.setKeys(Set.of(".*@alice\\.com", ".*@bob\\.com"));
    Matcher matcher = Matcher.build(config);

    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("jean@bob.com"));

    assertTrue(matcher.matches(message).matched());
  }

  @Test
  public void keysTakesPrecedenceOverKeyWhenBothAreSet() throws Exception {
    MailFilterRuleMatcherConfiguration config = new MailFilterRuleMatcherConfiguration();
    config.setType(MatcherType.FROM_ADDRESS_REGEXP);
    config.setKey(".*@ignored\\.com");
    config.setKeys(Set.of(".*@alice\\.com"));
    Matcher matcher = Matcher.build(config);

    MimeMessage ignoredKeyMessage = new MimeMessage(session);
    ignoredKeyMessage.setFrom(new InternetAddress("jean@ignored.com"));
    MimeMessage keysMessage = new MimeMessage(session);
    keysMessage.setFrom(new InternetAddress("jean@alice.com"));

    assertFalse(matcher.matches(ignoredKeyMessage).matched());
    assertTrue(matcher.matches(keysMessage).matched());
  }

  @Test
  public void failsFastAtBuildTimeWhenRegexpIsInvalid() {
    try {
      Matcher.build(configWithKey("[unclosed"));
      fail("expected an IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      // A typo'd regex must be rejected at startup, not deferred to the first message.
    }
  }

  @Test
  public void failsFastAtBuildTimeWhenNoKeyOrKeysConfigured() {
    MailFilterRuleMatcherConfiguration config = new MailFilterRuleMatcherConfiguration();
    config.setType(MatcherType.FROM_ADDRESS_REGEXP);
    try {
      Matcher.build(config);
      fail("expected an IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      // ok
    }
  }

  @Test
  public void extractKeyFromExampleIsNotSupported() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("alice@example.com"));

    try {
      new RegexpFromAddressMatcher().extractKeyFromExample(message);
      fail("should have thrown");
    } catch (UnsupportedOperationException expected) {
      // ok
    }
  }
}
