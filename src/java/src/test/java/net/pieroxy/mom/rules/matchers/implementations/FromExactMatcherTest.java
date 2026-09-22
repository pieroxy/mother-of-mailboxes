package net.pieroxy.mom.rules.matchers.implementations;

import net.pieroxy.mom.config.general.MailFilterRuleMatcherConfiguration;
import org.junit.Test;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FromExactMatcherTest {
  private final Session session = Session.getDefaultInstance(new Properties());

  private FromExactMatcher matcherFor(String key) {
    MailFilterRuleMatcherConfiguration config = new MailFilterRuleMatcherConfiguration();
    config.setKey(key);
    FromExactMatcher matcher = new FromExactMatcher();
    matcher.setConfig(config);
    return matcher;
  }

  @Test
  public void matchesExactFromAddress() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("alice@example.com"));

    assertTrue(matcherFor("alice@example.com").matches(message).matched());
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
    FromExactMatcher matcher = new FromExactMatcher();
    matcher.setConfig(config);

    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("bob@example.com"));

    assertTrue(matcher.matches(message).matched());
  }

  @Test
  public void extractKeyFromExampleReturnsFromAddress() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("alice@example.com"));

    assertEquals("alice@example.com", new FromExactMatcher().extractKeyFromExample(message));
  }

  @Test
  public void extractKeyFromExampleFailsWhenNoFromHeader() throws Exception {
    MimeMessage message = new MimeMessage(session);

    try {
      new FromExactMatcher().extractKeyFromExample(message);
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
      new FromExactMatcher().extractKeyFromExample(message);
      fail("should have thrown");
    } catch (MessagingException expected) {
      // ok
    }
  }

  @Test
  public void logsEachMatchAttemptAtDebugLevelWithTheFromAddress() throws Exception {
    MailFilterRuleMatcherConfiguration config = new MailFilterRuleMatcherConfiguration();
    config.setKey("debug-test@example.com");
    config.setLogLevel("DEBUG");
    FromExactMatcher matcher = new FromExactMatcher();
    matcher.setConfig(config);

    assertEquals("logLevel=DEBUG doit se traduire en Level.FINE", Level.FINE, matcher.getLogger().getLevel());

    List<LogRecord> records = new ArrayList<>();
    Handler capture = new Handler() {
      @Override public void publish(LogRecord record) { records.add(record); }
      @Override public void flush() {}
      @Override public void close() {}
    };
    matcher.getLogger().addHandler(capture);
    matcher.getLogger().setUseParentHandlers(false);
    try {
      MimeMessage message = new MimeMessage(session);
      message.setFrom(new InternetAddress("debug-test@example.com"));

      matcher.matches(message);
    } finally {
      matcher.getLogger().removeHandler(capture);
      matcher.getLogger().setUseParentHandlers(true);
    }

    assertEquals(1, records.size());
    assertEquals(Level.FINE, records.get(0).getLevel());
    assertTrue(records.get(0).getMessage().contains("debug-test@example.com"));
  }
}
