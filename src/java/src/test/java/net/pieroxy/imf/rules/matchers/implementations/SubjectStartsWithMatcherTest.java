package net.pieroxy.imf.rules.matchers.implementations;

import net.pieroxy.imf.config.general.MailFilterRuleMatcherConfiguration;
import org.junit.Test;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.util.Properties;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SubjectStartsWithMatcherTest {
  private final Session session = Session.getDefaultInstance(new Properties());

  private SubjectStartsWithMatcher matcherFor(String key) {
    MailFilterRuleMatcherConfiguration config = new MailFilterRuleMatcherConfiguration();
    config.setKey(key);
    SubjectStartsWithMatcher matcher = new SubjectStartsWithMatcher();
    matcher.setConfig(config);
    return matcher;
  }

  private MimeMessage messageWithSubject(String subject) throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setSubject(subject);
    return message;
  }

  @Test
  public void matchesWhenSubjectStartsWithKey() throws Exception {
    assertTrue(matcherFor("Your invoice").matches(messageWithSubject("Your invoice #12345 is ready")).matched());
  }

  @Test
  public void matchesCaseInsensitively() throws Exception {
    assertTrue(matcherFor("your invoice").matches(messageWithSubject("YOUR INVOICE #12345 is ready")).matched());
  }

  @Test
  public void doesNotMatchWhenPrefixIsElsewhereInSubject() throws Exception {
    assertFalse(matcherFor("invoice").matches(messageWithSubject("Re: Your invoice #12345")).matched());
  }

  @Test
  public void doesNotMatchWhenSubjectIsShorterThanKey() throws Exception {
    assertFalse(matcherFor("Your invoice is ready").matches(messageWithSubject("Your invoice")).matched());
  }

  @Test
  public void doesNotMatchWhenNoSubjectHeader() throws Exception {
    MimeMessage message = new MimeMessage(session);

    assertFalse(matcherFor("Your invoice").matches(message).matched());
  }

  @Test
  public void matchesAnyKeyWhenKeysIsUsedInsteadOfKey() throws Exception {
    MailFilterRuleMatcherConfiguration config = new MailFilterRuleMatcherConfiguration();
    config.setKeys(Set.of("Your invoice", "Payment reminder"));
    SubjectStartsWithMatcher matcher = new SubjectStartsWithMatcher();
    matcher.setConfig(config);

    assertTrue(matcher.matches(messageWithSubject("Payment reminder: account #987")).matched());
  }

  @Test
  public void extractKeyFromExampleReturnsFullSubjectVerbatim() throws Exception {
    MimeMessage message = messageWithSubject("Your invoice #12345 is ready");

    assertEquals("Your invoice #12345 is ready", new SubjectStartsWithMatcher().extractKeyFromExample(message));
  }

  @Test
  public void extractKeyFromExampleFailsWhenNoSubjectHeader() throws Exception {
    MimeMessage message = new MimeMessage(session);

    try {
      new SubjectStartsWithMatcher().extractKeyFromExample(message);
      fail("should have thrown");
    } catch (MessagingException expected) {
      // ok
    }
  }
}
