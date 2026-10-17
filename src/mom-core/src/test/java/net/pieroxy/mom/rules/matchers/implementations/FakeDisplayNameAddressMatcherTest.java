package net.pieroxy.mom.rules.matchers.implementations;

import net.pieroxy.mom.config.general.MailFilterRuleMatcherConfiguration;
import net.pieroxy.mom.detection.from.DisplayNameAddress;
import org.junit.Test;

import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FakeDisplayNameAddressMatcherTest {
  private final Session session = Session.getDefaultInstance(new Properties());

  private MimeMessage parse(String headers) throws Exception {
    String raw = headers + "Subject: test\r\n\r\nbody\r\n";
    return new MimeMessage(session, new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
  }

  private DisplayNameAddress of(String headers) throws Exception {
    return DisplayNameAddress.of(parse(headers));
  }

  @Test
  public void noAddressInDisplayName() throws Exception {
    assertEquals(DisplayNameAddress.NONE, of("From: Jean Dupont <jdupont@hotmail.com>\r\n"));
    assertEquals(DisplayNameAddress.NONE, of("From: jdupont@hotmail.com\r\n"));
    assertEquals(DisplayNameAddress.NONE, of("From: \"John @ Acme\" <john@acme.com>\r\n"));
    assertEquals(DisplayNameAddress.NONE, of("From: \"@handle\" <john@acme.com>\r\n"));
  }

  @Test
  public void invalidFromIsNone() throws Exception {
    assertEquals(DisplayNameAddress.NONE, of("From: \"service@paypal.com\" <a@x.com>\r\nFrom: b@y.com\r\n"));
    assertEquals(DisplayNameAddress.NONE, of("Sender: \"service@paypal.com\" <x@evil.ru>\r\n"));
  }

  @Test
  public void sameAddress() throws Exception {
    assertEquals(DisplayNameAddress.SAME, of("From: \"John@Example.com\" <john@example.com>\r\n"));
  }

  @Test
  public void sameOrganizationalDomain() throws Exception {
    assertEquals(DisplayNameAddress.SAME_DOMAIN, of("From: \"support@example.com\" <noreply@mail.example.com>\r\n"));
    assertEquals(DisplayNameAddress.SAME_DOMAIN, of("From: \"a@shop.example.co.uk\" <b@example.co.uk>\r\n"));
  }

  @Test
  public void otherDomain() throws Exception {
    assertEquals(DisplayNameAddress.OTHER_DOMAIN, of("From: \"service@paypal.com\" <x@evil.ru>\r\n"));
    assertEquals(DisplayNameAddress.OTHER_DOMAIN, of("From: PayPal (service@paypal.com) <x@evil.ru>\r\n"));
    assertEquals(DisplayNameAddress.OTHER_DOMAIN, of("From: =?UTF-8?Q?service=40paypal.com?= <x@evil.ru>\r\n"));
    // Two different registrable domains under the same public suffix.
    assertEquals(DisplayNameAddress.OTHER_DOMAIN, of("From: \"a@foo.co.uk\" <b@bar.co.uk>\r\n"));
  }

  @Test
  public void worstAddressWins() throws Exception {
    assertEquals(DisplayNameAddress.OTHER_DOMAIN,
        of("From: \"x@evil.ru and service@paypal.com\" <x@evil.ru>\r\n"));
  }

  @Test
  public void lookAlikeAtSignIsNormalized() throws Exception {
    assertEquals(DisplayNameAddress.OTHER_DOMAIN, of("From: \"service＠paypal.com\" <x@evil.ru>\r\n"));
  }

  @Test
  public void internationalizedAddresses() throws Exception {
    assertEquals(DisplayNameAddress.SAME, of("From: \"josé@exämple.com\" <josé@exämple.com>\r\n"));
    assertEquals(DisplayNameAddress.OTHER_DOMAIN, of("From: \"josé@exämple.com\" <x@evil.ru>\r\n"));
  }

  @Test
  public void matchesAnyOfTheConfiguredKeysCaseInsensitively() throws Exception {
    MailFilterRuleMatcherConfiguration config = new MailFilterRuleMatcherConfiguration();
    config.setKeys(Set.of("OTHER_DOMAIN", "same_domain"));
    FakeDisplayNameAddressMatcher matcher = new FakeDisplayNameAddressMatcher();
    matcher.setConfig(config);

    assertTrue(matcher.matches(parse("From: \"service@paypal.com\" <x@evil.ru>\r\n")).matched());
    assertTrue(matcher.matches(parse("From: \"support@example.com\" <noreply@example.com>\r\n")).matched());
    assertFalse(matcher.matches(parse("From: \"john@example.com\" <john@example.com>\r\n")).matched());
    assertFalse(matcher.matches(parse("From: Jean Dupont <jdupont@hotmail.com>\r\n")).matched());
  }
}
