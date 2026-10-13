package net.pieroxy.mom.rules.actions.implementations;

import net.pieroxy.mom.config.general.MailFilterRuleActionConfiguration;
import net.pieroxy.mom.rules.RuleContext;
import net.pieroxy.mom.rules.actions.Action;
import net.pieroxy.mom.rules.actions.ActionType;
import net.pieroxy.mom.utils.mail.RecordingMailSender;
import org.junit.Test;

import javax.mail.Flags;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ForwardActionTest {
  private static final String ORIGINAL = "From: Alice <alice@example.org>\r\n"
      + "To: me@example.com\r\n"
      + "Subject: Hello\r\n"
      + "Message-ID: <original@example.org>\r\n"
      + "DKIM-Signature: v=1; d=example.org; b=abc\r\n"
      + "\r\n"
      + "Original body\r\n";

  private final RecordingMailSender sender = new RecordingMailSender("me@example.com");
  private final Session session = Session.getInstance(new Properties());

  private Action action(ActionType type, String recipient) {
    MailFilterRuleActionConfiguration config = new MailFilterRuleActionConfiguration();
    config.setType(type);
    config.setKey(recipient);
    return Action.build(config, new RuleContext(null, null, null, null, 0, sender));
  }

  private MimeMessage original(String extraHeaders) throws Exception {
    return new MimeMessage(session, new ByteArrayInputStream((extraHeaders + ORIGINAL).getBytes(StandardCharsets.US_ASCII)));
  }

  private static byte[] bytesOf(MimeMessage message) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    message.writeTo(out);
    return out.toByteArray();
  }

  @Test
  public void forwardSendsANewMessageWithTheOriginalAttachedUnchanged() throws Exception {
    MimeMessage original = original("");

    assertTrue(action(ActionType.FORWARD, "other@example.net").run(original));

    assertEquals(1, sender.sent.size());
    assertEquals("other@example.net", sender.sent.get(0).recipient().toString());
    MimeMessage sent = sender.sent.get(0).message();
    assertEquals("me@example.com", sent.getFrom()[0].toString());
    assertEquals("Fwd: Hello", sent.getSubject());
    assertArrayEquals(new String[]{"me@example.com"}, sent.getHeader(ForwardAction.FORWARDED_BY_HEADER));
    MimeMultipart content = (MimeMultipart) new MimeMessage(session, new ByteArrayInputStream(bytesOf(sent))).getContent();
    assertTrue(content.getBodyPart(1).isMimeType("message/rfc822"));
    ByteArrayOutputStream attached = new ByteArrayOutputStream();
    content.getBodyPart(1).getDataHandler().writeTo(attached);
    assertEquals(ORIGINAL, attached.toString(StandardCharsets.US_ASCII));
  }

  @Test
  public void redirectResendsTheOriginalUntouchedWithResentHeaders() throws Exception {
    assertTrue(action(ActionType.REDIRECT, "other@example.net").run(original("")));

    MimeMessage sent = sender.sent.get(0).message();
    String raw = new String(bytesOf(sent), StandardCharsets.US_ASCII);
    assertTrue(raw, raw.contains("From: Alice <alice@example.org>\r\n"));
    assertTrue("the Message-ID must not be regenerated", raw.contains("Message-ID: <original@example.org>\r\n"));
    assertTrue(raw, raw.contains("DKIM-Signature: v=1; d=example.org; b=abc\r\n"));
    assertTrue(raw, raw.endsWith("\r\n\r\nOriginal body\r\n"));
    assertEquals("me@example.com", sent.getHeader("Resent-From")[0]);
    assertEquals("other@example.net", sent.getHeader("Resent-To")[0]);
    assertEquals(1, sent.getHeader("Resent-Date").length);
    assertTrue(sent.getHeader("Resent-Message-ID")[0].endsWith("@example.com>"));
    assertArrayEquals(new String[]{"me@example.com"}, sent.getHeader(ForwardAction.FORWARDED_BY_HEADER));
  }

  @Test
  public void skipsAMessageThisAccountAlreadyForwarded() throws Exception {
    MimeMessage original = original("X-MOM-Forwarded-By: someone@example.net\r\nX-MOM-Forwarded-By: ME@example.com\r\n");

    assertFalse(action(ActionType.FORWARD, "other@example.net").run(original));
    assertFalse(action(ActionType.REDIRECT, "other@example.net").run(original));

    assertTrue(sender.sent.isEmpty());
  }

  @Test
  public void aForwardKeepsThePreviousHopsSoLoopsAreCaughtFurtherOn() throws Exception {
    action(ActionType.FORWARD, "other@example.net").run(original("X-MOM-Forwarded-By: someone@example.net\r\n"));

    assertEquals(List.of("someone@example.net", "me@example.com"),
        List.of(sender.sent.get(0).message().getHeader(ForwardAction.FORWARDED_BY_HEADER)));
  }

  @Test
  public void neitherMarksTheMessageRead() throws Exception {
    MimeMessage original = original("");

    action(ActionType.FORWARD, "other@example.net").run(original);
    action(ActionType.REDIRECT, "other@example.net").run(original);

    assertFalse(original.isSet(Flags.Flag.SEEN));
  }

  @Test
  public void refusesToBuildWithoutSmtpSettings() {
    MailFilterRuleActionConfiguration config = new MailFilterRuleActionConfiguration();
    config.setType(ActionType.FORWARD);
    config.setKey("other@example.net");
    try {
      Action.build(config, RuleContext.EMPTY);
      fail("should have thrown");
    } catch (IllegalStateException expected) {
      assertTrue(expected.getMessage(), expected.getMessage().contains("SMTP"));
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void refusesAnInvalidRecipient() {
    action(ActionType.REDIRECT, "not an address");
  }

  @Test(expected = IllegalArgumentException.class)
  public void refusesSeveralRecipients() {
    action(ActionType.FORWARD, "a@example.net, b@example.net");
  }}
