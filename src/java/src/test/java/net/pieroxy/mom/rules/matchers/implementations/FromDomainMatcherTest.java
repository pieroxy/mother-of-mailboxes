package net.pieroxy.mom.rules.matchers.implementations;

import net.pieroxy.mom.config.general.MailFilterRuleMatcherConfiguration;
import org.junit.Test;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FromDomainMatcherTest {
  private final Session session = Session.getDefaultInstance(new Properties());

  private FromDomainMatcher matcherFor(String key) {
    MailFilterRuleMatcherConfiguration config = new MailFilterRuleMatcherConfiguration();
    config.setKey(key);
    FromDomainMatcher matcher = new FromDomainMatcher();
    matcher.setConfig(config);
    return matcher;
  }

  @Test
  public void matchesDomainIgnoringLocalPartAndDisplayName() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("jdupont@hotmail.com", "Jean Dupont"));

    assertTrue(matcherFor("hotmail.com").matches(message).matched());
  }

  @Test
  public void debugStringNamesTheClassAndTheKeyThatMatched() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("jdupont@gmail.com", "Jean Dupont"));

    assertEquals("FromDomainMatcher(gmail.com)", matcherFor("gmail.com").matches(message).debugString());
  }

  @Test
  public void debugStringNamesTheSpecificKeyAmongMultipleThatMatched() throws Exception {
    MailFilterRuleMatcherConfiguration config = new MailFilterRuleMatcherConfiguration();
    config.setKeys(Set.of("gmail.com", "hotmail.com"));
    FromDomainMatcher matcher = new FromDomainMatcher();
    matcher.setConfig(config);

    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("jdupont@Hotmail.com"));

    // The debug string names the candidate that matched (message's own casing), not the
    // configured key's casing — matching is case-insensitive via a lowercase lookup set, and
    // this string is for logs only (see Matcher.matchesKey).
    assertEquals("FromDomainMatcher(Hotmail.com)", matcher.matches(message).debugString());
  }

  @Test
  public void matchesCaseInsensitively() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("jdupont@Hotmail.COM"));

    assertTrue(matcherFor("hotmail.com").matches(message).matched());
  }

  @Test
  public void doesNotMatchDifferentDomain() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("alice@example.com"));

    assertFalse(matcherFor("other.com").matches(message).matched());
  }

  @Test
  public void doesNotMatchWhenNoFromHeader() throws Exception {
    MimeMessage message = new MimeMessage(session);

    assertFalse(matcherFor("example.com").matches(message).matched());
  }

  @Test
  public void doesNotMatchWhenMultipleFromAddresses() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.addFrom(new InternetAddress[]{
            new InternetAddress("alice@example.com"),
            new InternetAddress("bob@example.com")
    });

    assertFalse(matcherFor("example.com").matches(message).matched());
  }

  @Test
  public void matchesAnyKeyWhenKeysIsUsedInsteadOfKey() throws Exception {
    MailFilterRuleMatcherConfiguration config = new MailFilterRuleMatcherConfiguration();
    config.setKeys(Set.of("example.com", "other.com"));
    FromDomainMatcher matcher = new FromDomainMatcher();
    matcher.setConfig(config);

    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("alice@Other.com"));

    assertTrue(matcher.matches(message).matched());
  }

  @Test
  public void extractKeyFromExampleReturnsDomainOnly() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("alice@example.com", "Alice"));

    assertEquals("example.com", new FromDomainMatcher().extractKeyFromExample(message));
  }

  @Test
  public void extractKeyFromExampleFailsWhenNoFromHeader() throws Exception {
    MimeMessage message = new MimeMessage(session);

    try {
      new FromDomainMatcher().extractKeyFromExample(message);
      fail("should have thrown");
    } catch (MessagingException expected) {
      // ok
    }
  }

  @Test
  public void extractKeyFromExampleFailsWhenMultipleFromAddresses() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.addFrom(new InternetAddress[]{
            new InternetAddress("alice@example.com"),
            new InternetAddress("bob@example.com")
    });

    try {
      new FromDomainMatcher().extractKeyFromExample(message);
      fail("should have thrown");
    } catch (MessagingException expected) {
      // ok
    }
  }
}
