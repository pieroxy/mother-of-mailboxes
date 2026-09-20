package net.pieroxy.imf.mail;

import net.pieroxy.imf.utils.MailTools;
import net.pieroxy.imf.utils.mail.ImapMailboxConnection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.mail.Flags;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

import static org.junit.Assert.assertFalse;

/**
 * Regression test: reading a message's full content via
 * {@link MailTools#readRawMessageWithoutMarkingSeen} — what DkimResultMatcher and
 * DmarcResultMatcher do to verify a DKIM signature — must never mark that message as read on
 * the server. This is exactly the bug that was observed: no unread message was left in the
 * INBOX as soon as these matchers were in the rule list (whether or not a rule actually
 * matched).
 * <p>
 * Two traps that made the first fix attempts fail, kept here in a comment so they don't happen
 * again:
 * <ul>
 *   <li>The "mail.imap.peek" session property isn't enough on its own: it doesn't cover an
 *   ad-hoc content read like message.writeTo() (verified via an IMAP trace: javax.mail still
 *   sent BODY[], not BODY.PEEK[], whether or not the property was set).</li>
 *   <li>A {@link javax.mail.internet.MimeMessage} built in memory (as DkimResultMatcherTest
 *   does) can't catch this regression: writeTo() doesn't talk to any server there, so the side
 *   effect doesn't exist. Only a real IMAP message (GreenMail) can.</li>
 * </ul>
 */
public class ImapMailboxConnectionTest {
  private final GreenMailImapFixture fixture = new GreenMailImapFixture();

  @Before
  public void startServer() {
    fixture.start();
  }

  @After
  public void stopServer() {
    fixture.stop();
  }

  @Test
  public void readingTheFullMessageContentDoesNotMarkItSeen() throws Exception {
    Session session = Session.getDefaultInstance(new Properties());
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("sender@example.com"));
    message.setSubject("Test");
    message.setText("Hello");
    fixture.appendMessage(message, "INBOX");

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      Message[] messages = mailbox.getMessagesSince(0);
      MailTools.readRawMessageWithoutMarkingSeen(messages[0]);
    }

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      Message fetched = mailbox.getMessagesSince(0)[0];
      assertFalse("lire le contenu d'un message ne doit jamais le marquer \\Seen",
          fetched.isSet(Flags.Flag.SEEN));
    }
  }
}
