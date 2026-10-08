package net.pieroxy.mom.utils.mail;

import net.pieroxy.mom.config.general.MailAccountConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Exercises {@link ImapIdleWatcher} against a real in-memory IMAP server (GreenMail supports IDLE). */
public class ImapIdleWatcherTest {
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
  public void awaitReturnsAsSoonAsAMessageArrivesInTheInbox() throws Exception {
    MailAccountConfiguration config = fixture.accountConfig("idle-test");
    ImapIdleWatcher watcher = new ImapIdleWatcher(config, fixture.accountCredential(), (c, credential) -> fixture.connectStore());

    // Delivers repeatedly: a message appended before await() is actually in idle() triggers
    // nothing, and how long getting there takes depends on the machine's load.
    AtomicBoolean returned = new AtomicBoolean(false);
    Thread deliverer = new Thread(() -> {
      try {
        while (!returned.get()) {
          Thread.sleep(300);
          fixture.appendMessage(messageFrom("sender@example.com"), "INBOX");
        }
      } catch (Exception ignored) {
      }
    });
    deliverer.start();

    long start = System.currentTimeMillis();
    watcher.await(60_000); // huge budget: a correct await() must return long before this elapses
    long elapsed = System.currentTimeMillis() - start;
    returned.set(true);

    deliverer.join(2000);
    assertTrue("await() must return soon after the message arrives, not wait out the full budget (elapsed=" + elapsed + "ms)",
        elapsed < 5000);
  }

  /**
   * Mirrors exactly how {@code Runner.shutdown()} ends a wait: {@code interruptNow()} alone only
   * unblocks the current slice's {@code idle()} — the loop in {@link ImapIdleWatcher#await} would
   * otherwise just reconnect and start another slice. It's the combination with the waiting
   * thread's own interrupt flag (checked at the top of that loop) that actually ends the wait.
   */
  @Test
  public void interruptNowCombinedWithThreadInterruptEndsAnInProgressWaitRightAway() throws Exception {
    MailAccountConfiguration config = fixture.accountConfig("idle-test");
    ImapIdleWatcher watcher = new ImapIdleWatcher(config, fixture.accountCredential(), (c, credential) -> fixture.connectStore());

    Thread waiter = new Thread(() -> {
      try {
        watcher.await(60_000); // huge budget: the interrupt below must cut this short, not the budget itself
      } catch (InterruptedException ignored) {
      }
    });

    long start = System.currentTimeMillis();
    waiter.start();
    // Repeated: interruptNow() is a no-op until await() is actually in idle(), and how long
    // getting there takes depends on the machine's load.
    while (waiter.isAlive() && System.currentTimeMillis() - start < 5000) {
      Thread.sleep(300);
      waiter.interrupt();
      watcher.interruptNow();
      waiter.join(200);
    }
    long elapsed = System.currentTimeMillis() - start;

    assertFalse("the waiting thread must have ended, not still be blocked in idle()", waiter.isAlive());
    assertTrue("must end well before the 60s budget (elapsed=" + elapsed + "ms)", elapsed < 5000);
  }

  @Test
  public void awaitReturnsOnceTheBudgetIsExhaustedWhenNoMailArrives() throws Exception {
    MailAccountConfiguration config = fixture.accountConfig("idle-test");
    ImapIdleWatcher watcher = new ImapIdleWatcher(config, fixture.accountCredential(), (c, credential) -> fixture.connectStore());

    long start = System.currentTimeMillis();
    watcher.await(1000);
    long elapsed = System.currentTimeMillis() - start;

    assertTrue("await() must not return noticeably before its budget (elapsed=" + elapsed + "ms)", elapsed >= 900);
    assertTrue("await() must return once its budget is exhausted (elapsed=" + elapsed + "ms)", elapsed < 5000);
  }

  private MimeMessage messageFrom(String address) throws Exception {
    MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
    message.setFrom(new InternetAddress(address));
    message.setSubject("Test");
    message.setText("Hello");
    return message;
  }
}
