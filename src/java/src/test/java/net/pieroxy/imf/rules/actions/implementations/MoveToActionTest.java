package net.pieroxy.imf.rules.actions.implementations;

import net.pieroxy.imf.config.general.MailFilterRuleActionConfiguration;
import net.pieroxy.imf.utils.mail.GreenMailImapFixture;
import net.pieroxy.imf.utils.mail.ImapMailboxConnection;
import net.pieroxy.imf.rules.actions.Action;
import net.pieroxy.imf.rules.actions.ActionType;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Against a real in-memory IMAP server (GreenMail) — same reasoning as RuleLearner/
 * ManualReprocessor tests: MoveToAction needs a real Folder/Store, in particular to exercise
 * folder creation.
 */
public class MoveToActionTest {
  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  private final GreenMailImapFixture fixture = new GreenMailImapFixture();
  private final Session session = Session.getDefaultInstance(new Properties());

  @Before
  public void startServer() {
    fixture.start();
  }

  @After
  public void stopServer() {
    fixture.stop();
  }

  private MimeMessage messageFrom(String address) throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress(address));
    message.setSubject("Test");
    message.setText("Hello");
    return message;
  }

  private static Action moveToAction(String key) {
    MailFilterRuleActionConfiguration config = new MailFilterRuleActionConfiguration();
    config.setType(ActionType.MOVE_TO);
    config.setKey(key);
    return Action.build(config);
  }

  @Test
  public void movesToAFlatFolderCreatingItIfNeeded() throws Exception {
    fixture.appendMessage(messageFrom("sender@example.com"), "Source");

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      Folder source = mailbox.getOrCreateFolder("Source");
      Message message = mailbox.getAllMessages(source)[0];

      assertTrue(moveToAction("Spam").run(message));
    }

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      assertEquals(1, mailbox.getAllMessages(mailbox.getOrCreateFolder("Spam")).length);
    }
  }

  @Test
  public void movesToANestedFolderCreatingEveryMissingLevel() throws Exception {
    fixture.appendMessage(messageFrom("sender@example.com"), "Source");

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      Folder source = mailbox.getOrCreateFolder("Source");
      Message message = mailbox.getAllMessages(source)[0];

      // Neither "Admin - IT" nor "Admin - IT/Backups" exists yet: both levels must be created.
      assertTrue(moveToAction("Admin - IT/Backups").run(message));
    }

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      assertTrue("the intermediate folder must exist and be browsable on its own",
              mailbox.getOrCreateFolder("Admin - IT").exists());
      assertEquals("the message must land in the nested folder, not a single folder literally named \"Admin - IT/Backups\"",
              1, mailbox.getAllMessages(mailbox.getOrCreateFolder("Admin - IT", "Backups")).length);
    }
  }
}
