package net.pieroxy.mom.rules.matchers.implementations;

import net.pieroxy.mom.config.general.MailFilterRuleMatcherConfiguration;
import net.pieroxy.mom.rules.RuleContext;
import net.pieroxy.mom.rules.matchers.Matcher;
import net.pieroxy.mom.rules.matchers.MatcherType;
import net.pieroxy.mom.utils.mail.AccountAddresses;
import org.junit.Test;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RecipientMatcherTest {
  private static final List<String> ENVELOPE_HEADERS = List.of("Delivered-To", "X-Original-To");
  private static final AccountAddresses ADDRESSES = new AccountAddresses(List.of("me@example.com", "@example.net"));

  private final Session session = Session.getInstance(new Properties());

  private static RuleContext context(AccountAddresses addresses) {
    return new RuleContext(null, null, null, null, 0, null, addresses, ENVELOPE_HEADERS);
  }

  private static Matcher matcher(MatcherType type, String key) {
    MailFilterRuleMatcherConfiguration config = new MailFilterRuleMatcherConfiguration();
    config.setType(type);
    config.setKey(key);
    return Matcher.build(config, context(ADDRESSES));
  }

  private MimeMessage message(String headers) throws Exception {
    String raw = "From: alice@example.org\r\nSubject: Hello\r\n" + headers + "\r\nBody\r\n";
    return new MimeMessage(session, new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  public void matchesToAndCcIgnoringCase() throws Exception {
    Matcher matcher = matcher(MatcherType.RECIPIENT_ADDRESS_EQUALS, "Shop@Example.net");

    assertTrue(matcher.matches(message("To: Bob <bob@example.org>, shop@example.NET\r\n")).matched());
    assertTrue(matcher.matches(message("To: bob@example.org\r\nCc: shop@example.net\r\n")).matched());
    assertFalse(matcher.matches(message("To: bob@example.org\r\n")).matched());
  }

  @Test
  public void matchesTheTopmostEnvelopeHeaderOnly() throws Exception {
    Matcher matcher = matcher(MatcherType.RECIPIENT_ADDRESS_EQUALS, "shop@example.net");

    assertTrue(matcher.matches(message("Delivered-To: shop@example.net\r\nTo: list@example.org\r\n")).matched());
    assertTrue(matcher.matches(message("X-Original-To: shop@example.net\r\nTo: list@example.org\r\n")).matched());
    assertFalse("only the account's server's own occurrence counts",
        matcher.matches(message("Delivered-To: me@example.com\r\nDelivered-To: shop@example.net\r\nTo: list@example.org\r\n")).matched());
  }

  @Test
  public void ignoresHeadersThatArentConfigured() throws Exception {
    Matcher matcher = matcher(MatcherType.RECIPIENT_ADDRESS_EQUALS, "shop@example.net");

    assertFalse(matcher.matches(message("Envelope-To: shop@example.net\r\nTo: list@example.org\r\n")).matched());
  }

  @Test
  public void matchesTheDomain() throws Exception {
    Matcher matcher = matcher(MatcherType.RECIPIENT_DOMAIN_EQUALS, "example.net");

    assertTrue(matcher.matches(message("To: Shop@Example.net\r\n")).matched());
    assertFalse(matcher.matches(message("To: shop@sub.example.net\r\n")).matched());
  }

  @Test
  public void survivesAMalformedHeader() throws Exception {
    Matcher matcher = matcher(MatcherType.RECIPIENT_ADDRESS_EQUALS, "shop@example.net");

    assertTrue(matcher.matches(message("Delivered-To: <<<\r\nTo: shop@example.net\r\n")).matched());
  }

  @Test
  public void learnsTheAccountsOwnAddress() throws Exception {
    Matcher learner = Matcher.forLearning(MatcherType.RECIPIENT_ADDRESS_EQUALS, context(ADDRESSES));

    assertEquals("shop@example.net", learner.extractKeyFromExample(message("To: Bob <bob@example.org>, Shop@example.net\r\n")));
    assertEquals("shop@example.net", learner.extractKeyFromExample(message("Delivered-To: shop@example.net\r\nTo: list@example.org\r\n")));
  }

  @Test
  public void learnsTheAccountsOwnDomain() throws Exception {
    Matcher learner = Matcher.forLearning(MatcherType.RECIPIENT_DOMAIN_EQUALS, context(ADDRESSES));

    assertEquals("example.net", learner.extractKeyFromExample(message("To: a@example.net, b@example.net, bob@example.org\r\n")));
  }

  @Test
  public void refusesToLearnWithoutExactlyOneOwnAddress() throws Exception {
    Matcher learner = Matcher.forLearning(MatcherType.RECIPIENT_ADDRESS_EQUALS, context(ADDRESSES));

    assertRefused(learner, message("To: list@example.org\r\n"));
    assertRefused(learner, message("To: me@example.com, shop@example.net\r\n"));
  }

  @Test
  public void learnsTheOnlyRecipientOfAnAccountWithoutAddresses() throws Exception {
    Matcher learner = Matcher.forLearning(MatcherType.RECIPIENT_ADDRESS_EQUALS, context(AccountAddresses.NONE));

    assertEquals("me@example.com", learner.extractKeyFromExample(message("To: me@example.com\r\n")));
    assertRefused(learner, message("Delivered-To: me@example.com\r\nTo: list@example.org\r\n"));
  }

  private static void assertRefused(Matcher learner, MimeMessage message) {
    try {
      learner.extractKeyFromExample(message);
      fail("expected a MessagingException");
    } catch (MessagingException expected) {
      // expected
    }
  }
}
