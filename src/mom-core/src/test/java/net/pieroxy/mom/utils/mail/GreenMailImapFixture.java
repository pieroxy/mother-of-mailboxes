package net.pieroxy.mom.utils.mail;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import net.pieroxy.mom.config.credentials.Credential;
import net.pieroxy.mom.config.general.MailAccountConfiguration;
import net.pieroxy.mom.utils.mail.ImapMailbox;
import net.pieroxy.mom.utils.mail.ImapMailboxConnection;

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Store;
import java.util.Properties;

/**
 * Real IMAP test fixture for testing {@link net.pieroxy.mom.rules.MailAccount},
 * {@link net.pieroxy.mom.learning.RuleLearner} and {@link net.pieroxy.mom.rules.ManualReprocessor}:
 * starts an in-memory IMAP server (GreenMail) rather than hand-rolling a fake
 * {@code javax.mail.Folder} (an abstract class with about fifteen methods, including
 * {@code copyMessages}, which {@code MoveToAction}/{@code RuleLearner}/{@code
 * ManualReprocessor} rely on) — gives real Folder/Message objects that behave correctly, and
 * exercises the real {@link ImapMailboxConnection} along the way.
 */
public class GreenMailImapFixture {
  private static final String USERNAME = "test@localhost";
  private static final String PASSWORD = "password";

  // Dynamic port (assigned by the OS), not GreenMail's fixed test port: several instances of
  // this fixture run in parallel once tests are parallelized (see pom.xml), and a fixed port
  // would make those instances collide with each other.
  private final GreenMail greenMail = new GreenMail(ServerSetupTest.IMAP.dynamicPort());

  public void start() {
    greenMail.start();
    greenMail.setUser(USERNAME, USERNAME, PASSWORD);
  }

  public void stop() {
    greenMail.stop();
  }

  /** Config pointing at this server, ready to hand to a test MailAccount. */
  public MailAccountConfiguration accountConfig(String displayName) {
    MailAccountConfiguration config = new MailAccountConfiguration();
    config.setDisplayName(displayName);
    config.setHost("127.0.0.1");
    config.setPort(greenMail.getImap().getPort());
    config.setRunEvery(60);
    return config;
  }

  /** Resolved credential matching {@link #accountConfig}, ready to hand to a test MailAccount. */
  public Credential accountCredential() {
    Credential credential = new Credential();
    credential.setUsername(USERNAME);
    credential.setPassword(PASSWORD);
    return credential;
  }

  /** Plain (non-TLS) IMAP connection to this server — what connect() normally does via "imaps". */
  public ImapMailboxConnection connectAsImapMailbox() throws MessagingException {
    return ImapMailboxConnection.forTesting(connectStore());
  }

  /**
   * Raw {@link Store}, for tests that need lower-level access than {@link ImapMailbox} exposes
   * (e.g. {@code ImapIdleWatcherTest}, which needs message-count listeners on an {@code
   * IMAPFolder}). Plain (non-TLS) IMAP, like {@link #connectAsImapMailbox()}.
   */
  public Store connectStore() throws MessagingException {
    // mail.imap.peek: same setting as ImapMailboxConnection.connect() in production (see its
    // javadoc — NOT enough on its own for message.writeTo(), which also needs
    // IMAPMessage.setPeek() per message; see MailTools.readRawMessageWithoutMarkingSeen()).
    Properties props = new Properties();
    props.setProperty("mail.imap.peek", "true");
    Session session = Session.getDefaultInstance(props);
    Store store = session.getStore("imap");
    store.connect("127.0.0.1", greenMail.getImap().getPort(), USERNAME, PASSWORD);
    return store;
  }

  /** Appends message into the given folder (created if needed), via IMAP APPEND. */
  public void appendMessage(Message message, String... folderPath) throws MessagingException {
    try (ImapMailboxConnection mailbox = connectAsImapMailbox()) {
      Folder folder = mailbox.getOrCreateFolder(folderPath);
      folder.appendMessages(new Message[]{message});
    }
  }
}
