package net.pieroxy.imf.rules.matchers.implementations;

import net.pieroxy.imf.config.general.MailFilterRuleMatcherConfiguration;
import net.pieroxy.imf.detection.fcrdns.FakeFcrdnsDnsResolver;
import net.pieroxy.imf.detection.fcrdns.FcrdnsEvaluator;
import org.junit.Test;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FcrdnsResultMatcherTest {
  private final Session session = Session.getDefaultInstance(new Properties());

  private FcrdnsResultMatcher matcherWithEvaluator(String key, FcrdnsEvaluator evaluator) {
    MailFilterRuleMatcherConfiguration config = new MailFilterRuleMatcherConfiguration();
    config.setKey(key);
    FcrdnsResultMatcher matcher = new FcrdnsResultMatcher(evaluator);
    matcher.setConfig(config);
    return matcher;
  }

  private MimeMessage messageWithReceivedIp(String ip) throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.addHeader("Received", "from mail.example.com (mail.example.com [" + ip + "])\n"
            + "\tby mx.myprovider.com with SMTPS id abc; Mon, 31 Aug 2026 10:00:00 +0000");
    return message;
  }

  @Test
  public void matchesOnForwardConfirmedPtr() throws Exception {
    FakeFcrdnsDnsResolver resolver = new FakeFcrdnsDnsResolver()
            .withPtr("203.0.113.10", "mail.example.com")
            .withA("mail.example.com", "203.0.113.10");
    FcrdnsResultMatcher matcher = matcherWithEvaluator("pass", new FcrdnsEvaluator(resolver));

    assertTrue(matcher.matches(messageWithReceivedIp("203.0.113.10")).matched());
  }

  @Test
  public void matchesNoneWhenNoPtrRecord() throws Exception {
    FcrdnsResultMatcher matcher = matcherWithEvaluator("none", new FcrdnsEvaluator(new FakeFcrdnsDnsResolver()));

    assertTrue(matcher.matches(messageWithReceivedIp("203.0.113.10")).matched());
  }

  @Test
  public void doesNotMatchWhenClientIpCannotBeDetermined() throws Exception {
    FcrdnsResultMatcher matcher = matcherWithEvaluator("pass", new FcrdnsEvaluator(new FakeFcrdnsDnsResolver()));
    MimeMessage message = new MimeMessage(session); // pas de header Received

    assertFalse(matcher.matches(message).matched());
  }

  @Test
  public void extractKeyFromExampleReturnsFcrdnsResult() throws Exception {
    FakeFcrdnsDnsResolver resolver = new FakeFcrdnsDnsResolver()
            .withPtr("203.0.113.10", "mail.example.com")
            .withA("mail.example.com", "203.0.113.10");
    FcrdnsResultMatcher matcher = new FcrdnsResultMatcher(new FcrdnsEvaluator(resolver));

    assertEquals("pass", matcher.extractKeyFromExample(messageWithReceivedIp("203.0.113.10")));
  }

  @Test
  public void extractKeyFromExampleFailsWhenNoConnectingIp() throws Exception {
    FcrdnsResultMatcher matcher = new FcrdnsResultMatcher(new FcrdnsEvaluator(new FakeFcrdnsDnsResolver()));
    MimeMessage message = new MimeMessage(session);

    try {
      matcher.extractKeyFromExample(message);
      fail("should have thrown");
    } catch (MessagingException expected) {
      // ok
    }
  }
}
