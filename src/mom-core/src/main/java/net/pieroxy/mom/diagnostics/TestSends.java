package net.pieroxy.mom.diagnostics;

import net.pieroxy.mom.utils.mail.MailSender;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

/**
 * Test sends: a message sent through an account's own SMTP settings to the configured SMTP sink,
 * then fetched back from it, so the checks can see how the account's mail really reaches the
 * outside world (sending IP, HELO name, envelope sender, TLS…). One per diagnostics run, shared by
 * every check of that run: the webapp passes the same run id to each of them.
 * <p>
 * It all happens in the background, because delivery can take a while: a check only waits
 * {@link #WAIT_PER_CALL} for the message, then reports it as pending, and the webapp calls it
 * again — so no single request outlives a reverse proxy's timeout.
 */
public final class TestSends {
  private static final Logger LOGGER = Logger.getLogger(TestSends.class.getName());
  /** How long a check call waits for the message before reporting it as pending. */
  static Duration WAIT_PER_CALL = Duration.ofSeconds(20);
  /** How long the sink is polled for the message before giving up. */
  static Duration DELIVERY_TIMEOUT = Duration.ofMinutes(3);
  static Duration POLL_INTERVAL = Duration.ofSeconds(2);
  static final String RECIPIENT_LOCAL_PART = "mom-test";

  private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
    Thread t = new Thread(r, "test-send");
    t.setDaemon(true);
    return t;
  });
  /** The latest test send of each account: a new run replaces it. */
  private static final Map<String, TestSend> CURRENT = new ConcurrentHashMap<>();

  private TestSends() {}

  private record TestSend(String runId, long startedAt, CompletableFuture<ReceivedTestMessage> result) {}

  /**
   * The test send of this account's diagnostics run {@code runId} — started if it's the first
   * check of the run to ask — waiting up to {@link #WAIT_PER_CALL} for its message.
   */
  public static TestSendOutcome await(String accountName, String runId, MailSender sender, String sinkUrl) {
    TestSend send = CURRENT.compute(accountName, (name, current) ->
        current != null && current.runId.equals(runId) ? current : start(name, runId, sender, sinkUrl));
    try {
      return TestSendOutcome.received(send.result.get(WAIT_PER_CALL.toMillis(), TimeUnit.MILLISECONDS));
    } catch (TimeoutException e) {
      long seconds = (System.currentTimeMillis() - send.startedAt) / 1000;
      return TestSendOutcome.pending("Waiting for the test message (" + seconds + "s so far)…");
    } catch (ExecutionException e) {
      return TestSendOutcome.failed(e.getCause().getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return TestSendOutcome.failed("Interrupted.");
    }
  }

  private static TestSend start(String accountName, String runId, MailSender sender, String sinkUrl) {
    CompletableFuture<ReceivedTestMessage> result = new CompletableFuture<>();
    long startedAt = System.currentTimeMillis();
    EXECUTOR.submit(() -> {
      try {
        result.complete(sendAndFetch(accountName, sender, sinkUrl, startedAt));
      } catch (Exception e) {
        LOGGER.info("Test send [" + accountName + "] failed: " + e.getMessage());
        result.completeExceptionally(e);
      }
    });
    return new TestSend(runId, startedAt, result);
  }

  private static ReceivedTestMessage sendAndFetch(String accountName, MailSender sender, String sinkUrl, long startedAt) throws Exception {
    SmtpSinkClient.SinkInfo info;
    try {
      info = SmtpSinkClient.fetchInfo(sinkUrl);
    } catch (IOException e) {
      throw new Exception("Could not reach the SMTP sink: " + e.getMessage());
    }
    String uuid = UUID.randomUUID().toString();
    String recipient = RECIPIENT_LOCAL_PART + "@" + info.mailDomain();
    try {
      sender.send(buildMessage(sender, recipient, uuid), new InternetAddress(recipient));
    } catch (MessagingException e) {
      throw new Exception("Could not send the test message through this account's SMTP server: " + e.getMessage());
    }
    LOGGER.info("Test send [" + accountName + "] " + uuid + " sent to " + recipient);

    long deadline = startedAt + DELIVERY_TIMEOUT.toMillis();
    while (System.currentTimeMillis() < deadline) {
      Thread.sleep(POLL_INTERVAL.toMillis());
      Optional<byte[]> raw;
      try {
        raw = SmtpSinkClient.fetchMessage(sinkUrl, uuid);
      } catch (IOException e) {
        LOGGER.info("Test send [" + accountName + "] " + uuid + ": could not poll the SMTP sink (" + e.getMessage() + "), retrying");
        continue;
      }
      if (raw.isPresent()) {
        ReceivedTestMessage received = ReceivedTestMessage.parse(raw.get(), System.currentTimeMillis() - startedAt);
        LOGGER.info("Test send [" + accountName + "] " + uuid + " received after " + received.delayMs() / 1000 + "s from ["
            + received.clientIp() + "] helo=" + received.helo() + " tls=" + (received.tls() == null ? "none" : received.tls()));
        return received;
      }
    }
    throw new Exception("The test message didn't reach the SMTP sink within " + DELIVERY_TIMEOUT.toMinutes() + " minutes. "
        + "It may still be queued on your mail server, or have been rejected: look for a bounce in this account's inbox.");
  }

  private static MimeMessage buildMessage(MailSender sender, String recipient, String uuid) throws MessagingException {
    MimeMessage message = new MimeMessage(sender.getSession());
    message.setFrom(new InternetAddress(sender.getFromAddress()));
    message.setRecipient(Message.RecipientType.TO, new InternetAddress(recipient));
    message.setSubject(uuid);
    message.setSentDate(new Date());
    // RFC 3834: keeps vacation responders and the like from answering it.
    message.setHeader("Auto-Submitted", "auto-generated");
    message.setText("Test message sent by MOM to check this account's delivery settings. It can be deleted.\n", "UTF-8");
    message.saveChanges();
    return message;
  }
}
