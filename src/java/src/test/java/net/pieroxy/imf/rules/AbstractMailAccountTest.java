package net.pieroxy.imf.rules;

import net.pieroxy.imf.config.general.MailAccountConfiguration;
import net.pieroxy.imf.config.general.MailFilterRuleActionConfiguration;
import net.pieroxy.imf.config.general.MailFilterRuleConfiguration;
import net.pieroxy.imf.config.general.MailFilterRuleMatcherConfiguration;
import net.pieroxy.imf.utils.mail.GreenMailImapFixture;
import net.pieroxy.imf.rules.actions.ActionType;
import net.pieroxy.imf.rules.matchers.MatcherType;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.List;
import java.util.Properties;

/**
 * Fixture and helpers shared by the {@link MailAccount#processMessages()} tests against a real
 * in-memory IMAP server (GreenMail). One subclass per scenario (not a single class with 4
 * {@code @Test} methods): each {@code @Before}/{@code @After} starts/stops its own GreenMail
 * server, which dominates the run time by far — grouping them in one class would serialize
 * them, whereas separate classes run concurrently under {@code parallel=classes} (see pom.xml).
 * <p>
 * Named {@code Abstract*} to stay out of Surefire's discovery patterns (it has no {@code @Test}
 * of its own to run anyway).
 */
public abstract class AbstractMailAccountTest {
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

  protected static MailFilterRuleConfiguration moveToSpamOnDomain(String domain) {
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

  protected MimeMessage messageFrom(String address) throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress(address));
    message.setSubject("Test");
    message.setText("Hello");
    return message;
  }

  protected MailAccount accountWith(MailFilterRuleConfiguration... rules) {
    MailAccountConfiguration config = fixture.accountConfig("test-account");
    config.setRules(List.of(rules));
    return new MailAccount(config, fixture.accountCredential(), tempFolder.getRoot().getAbsolutePath(),
        (c, credential) -> fixture.connectAsImapMailbox());
  }
}
