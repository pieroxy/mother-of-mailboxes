package net.pieroxy.mom.rules.matchers.implementations;

import net.pieroxy.mom.config.general.MailFilterRuleMatcherConfiguration;
import net.pieroxy.mom.detection.from.FromValidity;
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

public class FromValidityMatcherTest {
  private final Session session = Session.getDefaultInstance(new Properties());

  private MimeMessage parse(String headers) throws Exception {
    String raw = headers + "Subject: test\r\n\r\nbody\r\n";
    return new MimeMessage(session, new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
  }

  private FromValidity validityOf(String headers) throws Exception {
    return FromValidity.of(parse(headers));
  }

  @Test
  public void singleWellFormedAddressIsValid() throws Exception {
    assertEquals(FromValidity.VALID, validityOf("From: Jean Dupont <jdupont@hotmail.com>\r\n"));
    assertEquals(FromValidity.VALID, validityOf("From: jdupont@hotmail.com\r\n"));
    assertEquals(FromValidity.VALID, validityOf("From: =?UTF-8?Q?Ren=C3=A9?=\r\n <rene@example.com>\r\n"));
  }

  @Test
  public void noFromHeaderIsMissingEvenWithASender() throws Exception {
    assertEquals(FromValidity.MISSING, validityOf("Sender: someone@example.com\r\n"));
    assertEquals(FromValidity.MISSING, validityOf("From: \r\n"));
  }

  @Test
  public void severalFromHeadersAreMultiple() throws Exception {
    assertEquals(FromValidity.MULTIPLE, validityOf("From: a@example.com\r\nFrom: b@example.com\r\n"));
  }

  @Test
  public void severalMailboxesInOneHeaderAreMultiple() throws Exception {
    assertEquals(FromValidity.MULTIPLE, validityOf("From: a@example.com, b@example.com\r\nSender: a@example.com\r\n"));
  }

  @Test
  public void unusableAddressIsInvalid() throws Exception {
    assertEquals(FromValidity.INVALID, validityOf("From: root\r\n"));
    assertEquals(FromValidity.INVALID, validityOf("From: Jean Dupont\r\n"));
    assertEquals(FromValidity.INVALID, validityOf("From: :>:;;\r\n"));
    assertEquals(FromValidity.INVALID, validityOf("From: a@@example.com\r\n"));
    assertEquals(FromValidity.INVALID, validityOf("From: undisclosed-recipients:;\r\n"));
  }

  @Test
  public void matchesAnyOfTheConfiguredKeysCaseInsensitively() throws Exception {
    MailFilterRuleMatcherConfiguration config = new MailFilterRuleMatcherConfiguration();
    config.setKeys(Set.of("MISSING", "invalid", "Multiple"));
    FromValidityMatcher matcher = new FromValidityMatcher();
    matcher.setConfig(config);

    assertTrue(matcher.matches(parse("Sender: someone@example.com\r\n")).matched());
    assertTrue(matcher.matches(parse("From: root\r\n")).matched());
    assertTrue(matcher.matches(parse("From: a@example.com\r\nFrom: b@example.com\r\n")).matched());
    assertFalse(matcher.matches(parse("From: jdupont@hotmail.com\r\n")).matched());
  }
}
