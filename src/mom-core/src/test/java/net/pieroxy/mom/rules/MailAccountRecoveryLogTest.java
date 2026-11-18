package net.pieroxy.mom.rules;

import net.pieroxy.mom.config.general.MailAccountConfiguration;
import net.pieroxy.mom.utils.LogCapture;
import org.junit.Test;

import javax.mail.MessagingException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;

public class MailAccountRecoveryLogTest extends AbstractMailAccountTest {
  @Test
  public void theEndOfARunOfFailedCyclesIsLoggedOnce() throws Exception {
    MailAccountConfiguration config = fixture.accountConfig("test-account");
    config.setRules(List.of());
    AtomicInteger failuresLeft = new AtomicInteger();
    MailAccount account = new MailAccount(config, fixture.accountCredential(), tempFolder.getRoot().getAbsolutePath(), (c, credential) -> {
      if (failuresLeft.getAndDecrement() > 0) throw new MessagingException("server says no");
      return fixture.connectAsImapMailbox();
    });

    try (LogCapture logs = new LogCapture(Logger.getLogger(MailAccount.class.getName()))) {
      account.processMessagesTracked();
      assertEquals("nothing to recover from yet", 0, logs.count("recovered after"));

      failuresLeft.set(2);
      for (int i = 0; i < 2; i++) {
        try {
          account.processMessagesTracked();
        } catch (MessagingException expected) {
        }
      }
      account.processMessagesTracked();
      account.processMessagesTracked();

      assertEquals(1, logs.count("recovered after 2 failed cycle(s), first failure at "));
    }
  }
}
