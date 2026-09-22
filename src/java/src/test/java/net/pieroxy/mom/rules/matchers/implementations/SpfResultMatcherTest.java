package net.pieroxy.mom.rules.matchers.implementations;

import net.pieroxy.mom.config.general.MailFilterRuleMatcherConfiguration;
import net.pieroxy.mom.spf.FakeSpfDnsResolver;
import net.pieroxy.mom.detection.spf.SpfEvaluator;
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

public class SpfResultMatcherTest {
  private final Session session = Session.getDefaultInstance(new Properties());

  private SpfResultMatcher matcherWithEvaluator(String key, SpfEvaluator evaluator) {
    MailFilterRuleMatcherConfiguration config = new MailFilterRuleMatcherConfiguration();
    config.setKey(key);
    SpfResultMatcher matcher = new SpfResultMatcher(evaluator);
    matcher.setConfig(config);
    return matcher;
  }

  private MimeMessage messageWithReceivedAndFrom(String ip, String fromAddress) throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.addHeader("Received", "from mail.example.com (mail.example.com [" + ip + "])\n"
            + "\tby mx.myprovider.com with SMTPS id abc; Mon, 31 Aug 2026 10:00:00 +0000");
    message.setFrom(new InternetAddress(fromAddress));
    return message;
  }

  @Test
  public void matchesViaLiveDnsEvaluation() throws Exception {
    FakeSpfDnsResolver resolver = new FakeSpfDnsResolver()
            .withTxt("example.com", "v=spf1 ip4:203.0.113.0/24 -all");
    SpfResultMatcher matcher = matcherWithEvaluator("pass", new SpfEvaluator(resolver));

    MimeMessage message = messageWithReceivedAndFrom("203.0.113.10", "sender@example.com");

    assertTrue(matcher.matches(message).matched());
  }

  @Test
  public void liveDnsEvaluationCanFail() throws Exception {
    FakeSpfDnsResolver resolver = new FakeSpfDnsResolver()
            .withTxt("example.com", "v=spf1 ip4:203.0.113.0/24 -all");
    SpfResultMatcher matcher = matcherWithEvaluator("fail", new SpfEvaluator(resolver));

    MimeMessage message = messageWithReceivedAndFrom("198.51.100.1", "spoofed@example.com");

    assertTrue(matcher.matches(message).matched());
  }

  @Test
  public void matchesCaseInsensitively() throws Exception {
    FakeSpfDnsResolver resolver = new FakeSpfDnsResolver()
            .withTxt("example.com", "v=spf1 ip4:203.0.113.0/24 -all");
    SpfResultMatcher matcher = matcherWithEvaluator("PASS", new SpfEvaluator(resolver));

    MimeMessage message = messageWithReceivedAndFrom("203.0.113.10", "sender@example.com");

    assertTrue(matcher.matches(message).matched());
  }

  @Test
  public void matchesAnyKeyWhenKeysIsUsedInsteadOfKey() throws Exception {
    FakeSpfDnsResolver resolver = new FakeSpfDnsResolver()
            .withTxt("example.com", "v=spf1 ip4:203.0.113.0/24 ~all");
    MailFilterRuleMatcherConfiguration config = new MailFilterRuleMatcherConfiguration();
    config.setKeys(Set.of("fail", "softfail"));
    SpfResultMatcher matcher = new SpfResultMatcher(new SpfEvaluator(resolver));
    matcher.setConfig(config);

    MimeMessage message = messageWithReceivedAndFrom("198.51.100.1", "sender@example.com");

    assertTrue(matcher.matches(message).matched());
  }

  /**
   * An Authentication-Results/Received-SPF header can't be trusted regardless of where it comes
   * from (the sender could have forged it themselves): it's never trusted, the live check is
   * always authoritative, even when such a header claims otherwise.
   */
  @Test
  public void ignoresPreexistingAuthenticationResultsHeaderEvenWhenPresent() throws Exception {
    FakeSpfDnsResolver resolver = new FakeSpfDnsResolver()
            .withTxt("example.com", "v=spf1 ip4:203.0.113.0/24 -all");
    SpfResultMatcher matcher = matcherWithEvaluator("fail", new SpfEvaluator(resolver));

    MimeMessage message = messageWithReceivedAndFrom("198.51.100.1", "spoofed@example.com");
    message.addHeader("Authentication-Results", "mx.forged.example; spf=pass smtp.mailfrom=spoofed@example.com");

    assertTrue(matcher.matches(message).matched()); // "fail" matches: the pre-existing pass header is ignored
  }

  @Test
  public void doesNotMatchWhenClientIpCannotBeDetermined() throws Exception {
    FakeSpfDnsResolver resolver = new FakeSpfDnsResolver()
            .withTxt("example.com", "v=spf1 ip4:203.0.113.0/24 -all");
    SpfResultMatcher matcher = matcherWithEvaluator("pass", new SpfEvaluator(resolver));

    MimeMessage message = new MimeMessage(session); // pas de header Received
    message.setFrom(new InternetAddress("sender@example.com"));

    assertFalse(matcher.matches(message).matched());
  }

  @Test
  public void extractKeyFromExampleReturnsLiveSpfResult() throws Exception {
    FakeSpfDnsResolver resolver = new FakeSpfDnsResolver()
            .withTxt("example.com", "v=spf1 ip4:203.0.113.0/24 -all");
    SpfResultMatcher matcher = new SpfResultMatcher(new SpfEvaluator(resolver));

    MimeMessage message = messageWithReceivedAndFrom("203.0.113.10", "sender@example.com");

    assertEquals("pass", matcher.extractKeyFromExample(message));
  }

  @Test
  public void extractKeyFromExampleFailsWhenClientIpCannotBeDetermined() throws Exception {
    FakeSpfDnsResolver resolver = new FakeSpfDnsResolver();
    SpfResultMatcher matcher = new SpfResultMatcher(new SpfEvaluator(resolver));

    MimeMessage message = new MimeMessage(session);

    try {
      matcher.extractKeyFromExample(message);
      fail("should have thrown");
    } catch (MessagingException expected) {
      // ok
    }
  }
}
