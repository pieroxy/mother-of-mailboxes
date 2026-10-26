package net.pieroxy.mom.rules.actions.implementations;

import net.pieroxy.mom.config.general.MailFilterRuleActionConfiguration;
import net.pieroxy.mom.rules.RuleContext;
import net.pieroxy.mom.rules.actions.Action;
import net.pieroxy.mom.rules.actions.ActionType;
import net.pieroxy.mom.utils.mail.RecordingMailSender;
import org.junit.Test;

import javax.mail.Flags;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ReplyActionTest {
  private static final String HEADERS = "From: Alice <alice@example.org>\r\n"
      + "To: me@example.com\r\n"
      + "Subject: Hello\r\n"
      + "Message-ID: <original@example.org>\r\n";
  private static final String BODY = "\r\nOriginal body\r\n";

  private final RecordingMailSender sender = new RecordingMailSender("me@example.com", List.of("contact@example.com", "@example.net"));
  private final Session session = Session.getInstance(new Properties());

  private Action action(String subject, String body) {
    MailFilterRuleActionConfiguration config = new MailFilterRuleActionConfiguration();
    config.setType(ActionType.REPLY);
    config.setKey(subject);
    config.setBody(body);
    return Action.build(config, new RuleContext(null, null, null, null, 0, sender));
  }

  private Action action() {
    return action(null, "I'm away until Monday.");
  }

  private MimeMessage message(String headers) throws Exception {
    return new MimeMessage(session, new ByteArrayInputStream((headers + BODY).getBytes(StandardCharsets.UTF_8)));
  }

  private MimeMessage original(String extraHeaders) throws Exception {
    return message(extraHeaders + HEADERS);
  }

  @Test
  public void repliesToTheSenderInTheSameThread() throws Exception {
    assertTrue(action().run(original("References: <first@example.org>\r\n")));

    assertEquals(1, sender.sent.size());
    MimeMessage reply = sender.sent.get(0).message();
    assertEquals("alice@example.org", ((InternetAddress) sender.sent.get(0).recipient()).getAddress());
    assertEquals("me@example.com", ((InternetAddress) reply.getFrom()[0]).getAddress());
    assertEquals("Re: Hello", reply.getSubject());
    assertArrayEquals(new String[]{"<original@example.org>"}, reply.getHeader("In-Reply-To"));
    assertArrayEquals(new String[]{"<first@example.org> <original@example.org>"}, reply.getHeader("References"));
    assertArrayEquals(new String[]{"auto-replied"}, reply.getHeader("Auto-Submitted"));
    assertEquals("I'm away until Monday.", reply.getContent());
  }

  @Test
  public void prefersReplyTo() throws Exception {
    action().run(original("Reply-To: support@example.org\r\n"));

    assertEquals("support@example.org", ((InternetAddress) sender.sent.get(0).recipient()).getAddress());
  }

  @Test
  public void usesTheConfiguredSubject() throws Exception {
    action("Out of office", "Back on Monday.").run(original(""));

    assertEquals("Out of office", sender.sent.get(0).message().getSubject());
  }

  @Test
  public void doesNotStackRePrefixes() throws Exception {
    action().run(message(HEADERS.replace("Subject: Hello", "Subject: RE: Hello")));

    assertEquals("RE: Hello", sender.sent.get(0).message().getSubject());
  }

  @Test
  public void keepsNonAsciiText() throws Exception {
    action("Absent · été", "Je suis absent jusqu'à lundi.").run(original(""));

    MimeMessage reply = sender.sent.get(0).message();
    assertEquals("Absent · été", reply.getSubject());
    assertEquals("Je suis absent jusqu'à lundi.", reply.getContent());
  }

  @Test
  public void leavesTheMessageUnread() throws Exception {
    MimeMessage original = original("");

    action().run(original);

    assertFalse(original.isSet(Flags.Flag.SEEN));
  }

  @Test
  public void repliesWhenAutoSubmittedIsNo() throws Exception {
    assertTrue(action().run(original("Auto-Submitted: no\r\n")));
  }

  @Test
  public void skipsAutomatedMessages() throws Exception {
    assertSkipped("Auto-Submitted: auto-replied\r\n");
    assertSkipped("Auto-Submitted: Auto-Generated; foo=bar\r\n");
  }

  @Test
  public void skipsBulkAndListMessages() throws Exception {
    assertSkipped("Precedence: bulk\r\n");
    assertSkipped("Precedence: list\r\n");
    assertSkipped("Precedence: junk\r\n");
    assertSkipped("List-Id: <news.example.org>\r\n");
    assertSkipped("List-Unsubscribe: <mailto:leave@example.org>\r\n");
    assertSkipped("List-Post: <mailto:list@example.org>\r\n");
  }

  @Test
  public void skipsBounces() throws Exception {
    assertSkipped("Return-Path: <>\r\n");
  }

  @Test
  public void skipsAutomatedSenderAddresses() throws Exception {
    for (String address : new String[]{"noreply@example.org", "no-reply@example.org", "No_Reply.billing@example.org",
        "do-not-reply@example.org", "MAILER-DAEMON@example.org", "postmaster@example.org",
        "owner-news@example.org", "news-request@example.org"}) {
      assertFalse(address, action().run(message(HEADERS.replace("alice@example.org", address))));
    }
    assertTrue(sender.sent.isEmpty());
  }

  @Test
  public void skipsTheAccountsOwnMessages() throws Exception {
    assertFalse(action().run(message(HEADERS.replace("alice@example.org", "ME@example.com"))));
    assertFalse(action().run(original("Reply-To: me@example.com\r\n")));
    assertTrue(sender.sent.isEmpty());
  }

  @Test
  public void repliesWhenAnAliasIsInToOrCc() throws Exception {
    assertTrue(action().run(message(HEADERS.replace("To: me@example.com", "To: Contact@Example.com"))));
    assertTrue(action().run(message(HEADERS.replace("To: me@example.com", "To: anyone@example.net"))));
    assertTrue(action().run(message(HEADERS.replace("To: me@example.com", "To: list@example.org\r\nCc: me@example.com"))));
    assertEquals(3, sender.sent.size());
  }

  @Test
  public void skipsMessagesNotAddressedToTheAccount() throws Exception {
    assertFalse(action().run(message(HEADERS.replace("To: me@example.com", "To: list@example.org"))));
    assertFalse("a domain alias doesn't cover its subdomains",
        action().run(message(HEADERS.replace("To: me@example.com", "To: me@sub.example.net"))));
    assertFalse(action().run(message(HEADERS.replace("To: me@example.com\r\n", ""))));
    assertTrue(sender.sent.isEmpty());
  }

  @Test
  public void skipsMessagesFromAnAlias() throws Exception {
    assertFalse(action().run(message(HEADERS.replace("alice@example.org", "contact@example.com"))));
    assertFalse(action().run(message(HEADERS.replace("alice@example.org", "someone@example.net"))));
    assertTrue(sender.sent.isEmpty());
  }

  @Test
  public void skipsMessagesWithoutSender() throws Exception {
    assertFalse(action().run(message(HEADERS.replace("From: Alice <alice@example.org>\r\n", ""))));
    assertTrue(sender.sent.isEmpty());
  }

  @Test
  public void refusesToBuildWithoutSmtpSettings() {
    MailFilterRuleActionConfiguration config = new MailFilterRuleActionConfiguration();
    config.setType(ActionType.REPLY);
    config.setBody("Away.");
    try {
      Action.build(config, RuleContext.EMPTY);
      fail("should have thrown");
    } catch (IllegalStateException expected) {
      assertTrue(expected.getMessage(), expected.getMessage().contains("SMTP"));
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void refusesABlankBody() {
    action("Out of office", "  ");
  }

  private void assertSkipped(String extraHeaders) throws Exception {
    assertFalse(extraHeaders, action().run(original(extraHeaders)));
    assertTrue(sender.sent.isEmpty());
  }
}
