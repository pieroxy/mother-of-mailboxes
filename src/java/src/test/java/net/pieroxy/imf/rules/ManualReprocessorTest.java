package net.pieroxy.imf.rules;

import net.pieroxy.imf.config.general.MailFilterRuleActionConfiguration;
import net.pieroxy.imf.config.general.MailFilterRuleConfiguration;
import net.pieroxy.imf.config.general.MailFilterRuleMatcherConfiguration;
import net.pieroxy.imf.learning.LearnedRulesStore;
import net.pieroxy.imf.utils.mail.GreenMailImapFixture;
import net.pieroxy.imf.utils.mail.ImapMailboxConnection;
import net.pieroxy.imf.rules.actions.ActionType;
import net.pieroxy.imf.rules.matchers.MatcherType;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.mail.Flags;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests ManualReprocessor against a real in-memory IMAP server (GreenMail) — same reasons as
 * RuleLearnerTest: moveToDone() needs a real Folder attached to the message.
 */
public class ManualReprocessorTest {
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

  private RuleCatalog catalogWith(MailFilterRuleConfiguration... rules) {
    LearnedRulesStore learnedRulesStore = new LearnedRulesStore(tempFolder.getRoot().getAbsolutePath(), "test-account");
    return new RuleCatalog(List.of(rules), learnedRulesStore);
  }

  private static MailFilterRuleConfiguration moveToSpamOnDomain(String domain) {
    MailFilterRuleMatcherConfiguration matcher = new MailFilterRuleMatcherConfiguration();
    matcher.setType(MatcherType.FROM_DOMAIN_EQUALS);
    matcher.setKey(domain);
    MailFilterRuleActionConfiguration action = new MailFilterRuleActionConfiguration();
    action.setType(ActionType.MOVE_TO);
    action.setKey("Spam");
    MailFilterRuleConfiguration rule = new MailFilterRuleConfiguration();
    rule.setMatcher(matcher);
    rule.setAction(action);
    return rule;
  }

  private MimeMessage messageFrom(String address) throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress(address));
    message.setSubject("Test");
    message.setText("Hello");
    return message;
  }

  @Test
  public void ensureFolderSkeletonCreatesToProcess() throws Exception {
    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      new ManualReprocessor(mailbox, catalogWith()).ensureFolderSkeleton();

      assertTrue(mailbox.getOrCreateFolder("imf-rules", "ToProcess").exists());
    }
  }

  @Test
  public void matchingMessageIsRelocatedByItsRuleAndNotLeftInToProcessOrDone() throws Exception {
    fixture.appendMessage(messageFrom("sender@spammy.example.com"), "imf-rules", "ToProcess");

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      ManualReprocessor reprocessor = new ManualReprocessor(mailbox, catalogWith(moveToSpamOnDomain("spammy.example.com")));
      reprocessor.ensureFolderSkeleton();
      reprocessor.reprocessPending();
    }

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      assertEquals(1, mailbox.getAllMessages(mailbox.getOrCreateFolder("Spam")).length);
      assertEquals(0, mailbox.getAllMessages(mailbox.getOrCreateFolder("imf-rules", "ToProcess")).length);
      assertEquals(0, mailbox.getAllMessages(mailbox.getOrCreateFolder("imf-rules", "Done")).length);
    }
  }

  @Test
  public void nonMatchingMessageEndsUpInDone() throws Exception {
    fixture.appendMessage(messageFrom("sender@unrelated.example.com"), "imf-rules", "ToProcess");

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      ManualReprocessor reprocessor = new ManualReprocessor(mailbox, catalogWith(moveToSpamOnDomain("spammy.example.com")));
      reprocessor.ensureFolderSkeleton();
      reprocessor.reprocessPending();
    }

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      assertEquals(1, mailbox.getAllMessages(mailbox.getOrCreateFolder("imf-rules", "Done")).length);
      assertEquals(0, mailbox.getAllMessages(mailbox.getOrCreateFolder("imf-rules", "ToProcess")).length);
    }
  }

  @Test
  public void messageFiledIntoDoneIsMarkedUnread() throws Exception {
    MimeMessage message = messageFrom("sender@unrelated.example.com");
    message.setFlag(Flags.Flag.SEEN, true); // starts read: proves moveToDone() actively clears it
    fixture.appendMessage(message, "imf-rules", "ToProcess");

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      ManualReprocessor reprocessor = new ManualReprocessor(mailbox, catalogWith(moveToSpamOnDomain("spammy.example.com")));
      reprocessor.ensureFolderSkeleton();
      reprocessor.reprocessPending();
    }

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      Message[] inDone = mailbox.getAllMessages(mailbox.getOrCreateFolder("imf-rules", "Done"));
      assertEquals(1, inDone.length);
      assertFalse("a message filed into Done must stand out as needing attention in the mail client",
              inDone[0].isSet(Flags.Flag.SEEN));
    }
  }
}
