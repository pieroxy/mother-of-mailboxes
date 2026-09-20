package net.pieroxy.imf.learning;

import net.pieroxy.imf.utils.mail.GreenMailImapFixture;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

/**
 * Fixture and helpers shared by the {@link RuleLearner} tests against a real in-memory IMAP
 * server (GreenMail). One subclass per scenario rather than a single class with several
 * {@code @Test} methods: each {@code @Before}/{@code @After} starts/stops its own GreenMail
 * server (the real cost, a few seconds), so separating them lets them run in parallel with the
 * rest (see {@code parallel=classes} in pom.xml) instead of stacking up in a single sequential
 * class — same reasoning as {@code AbstractMailAccountTest}.
 */
public abstract class AbstractRuleLearnerTest {
  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  protected final GreenMailImapFixture fixture = new GreenMailImapFixture();
  private final Session session = Session.getDefaultInstance(new Properties());

  @Before
  public void startServer() {
    fixture.start();
  }

  @After
  public void stopServer() {
    fixture.stop();
  }

  protected LearnedRulesStore store() {
    return new LearnedRulesStore(tempFolder.getRoot().getAbsolutePath(), "test-account");
  }

  protected MimeMessage messageFrom(String address) throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress(address));
    message.setSubject("Test");
    message.setText("Hello");
    return message;
  }
}
