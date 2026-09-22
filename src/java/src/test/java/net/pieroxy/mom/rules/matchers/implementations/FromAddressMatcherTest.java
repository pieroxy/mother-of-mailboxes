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

public class FromAddressMatcherTest {
  private final Session session = Session.getDefaultInstance(new Properties());

  private FromAddressMatcher matcherFor(String key) {
    MailFilterRuleMatcherConfiguration config = new MailFilterRuleMatcherConfiguration();
    config.setKey(key);
    FromAddressMatcher matcher = new FromAddressMatcher();
    matcher.setConfig(config);
    return matcher;
  }

  @Test
  public void matchesAddressIgnoringDisplayName() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("jdupont@hotmail.com", "Jean Dupont"));

    assertTrue(matcherFor("jdupont@hotmail.com").matches(message).matched());
  }

  @Test
  public void matchesCaseInsensitively() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("JDupont@Hotmail.com", "Jean Dupont"));

    assertTrue(matcherFor("jdupont@hotmail.com").matches(message).matched());
  }

  @Test
  public void doesNotMatchDifferentAddress() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("alice@example.com"));

    assertFalse(matcherFor("bob@example.com").matches(message).matched());
  }

  @Test
  public void doesNotMatchWhenNoFromHeader() throws Exception {
    MimeMessage message = new MimeMessage(session);

    assertFalse(matcherFor("alice@example.com").matches(message).matched());
  }

  @Test
  public void doesNotMatchWhenMultipleFromAddresses() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.addFrom(new InternetAddress[]{
            new InternetAddress("alice@example.com"),
            new InternetAddress("bob@example.com")
    });

    assertFalse(matcherFor("alice@example.com").matches(message).matched());
  }

  @Test
  public void matchesAnyKeyWhenKeysIsUsedInsteadOfKey() throws Exception {
    MailFilterRuleMatcherConfiguration config = new MailFilterRuleMatcherConfiguration();
    config.setKeys(Set.of("alice@example.com", "bob@example.com"));
    FromAddressMatcher matcher = new FromAddressMatcher();
    matcher.setConfig(config);

    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("Bob@Example.com"));

    assertTrue(matcher.matches(message).matched());
  }

  @Test
  public void keysTakesPrecedenceOverKeyWhenBothAreSet() throws Exception {
    MailFilterRuleMatcherConfiguration config = new MailFilterRuleMatcherConfiguration();
    config.setKey("ignored@example.com");
    config.setKeys(Set.of("alice@example.com"));
    FromAddressMatcher matcher = new FromAddressMatcher();
    matcher.setConfig(config);

    MimeMessage ignoredKeyMessage = new MimeMessage(session);
    ignoredKeyMessage.setFrom(new InternetAddress("ignored@example.com"));
    MimeMessage keysMessage = new MimeMessage(session);
    keysMessage.setFrom(new InternetAddress("alice@example.com"));

    assertFalse(matcher.matches(ignoredKeyMessage).matched());
    assertTrue(matcher.matches(keysMessage).matched());
  }

  @Test
  public void extractKeyFromExampleReturnsAddressWithoutDisplayName() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("alice@example.com", "Alice"));

    assertEquals("alice@example.com", new FromAddressMatcher().extractKeyFromExample(message));
  }

  @Test
  public void extractKeyFromExampleFailsWhenNoFromHeader() throws Exception {
    MimeMessage message = new MimeMessage(session);

    try {
      new FromAddressMatcher().extractKeyFromExample(message);
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
      new FromAddressMatcher().extractKeyFromExample(message);
      fail("should have thrown");
    } catch (MessagingException expected) {
      // ok
    }
  }
}
