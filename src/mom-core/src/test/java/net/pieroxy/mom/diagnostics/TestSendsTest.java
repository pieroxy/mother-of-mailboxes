package net.pieroxy.mom.diagnostics;

import com.sun.net.httpserver.HttpServer;
import net.pieroxy.mom.utils.mail.MailSender;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.mail.Address;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** A test send against a fake sink (JDK HTTP server) and a fake mail sender that "delivers" to it. */
public class TestSendsTest {
  private HttpServer sink;
  private String sinkUrl;
  private final Map<String, byte[]> sinkMessages = new ConcurrentHashMap<>();
  private Duration savedWait, savedTimeout, savedPoll;

  @Before
  public void setUp() throws IOException {
    savedWait = TestSends.WAIT_PER_CALL;
    savedTimeout = TestSends.DELIVERY_TIMEOUT;
    savedPoll = TestSends.POLL_INTERVAL;
    TestSends.POLL_INTERVAL = Duration.ofMillis(50);
    TestSends.WAIT_PER_CALL = Duration.ofSeconds(5);
    sink = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    sink.createContext("/info", e -> respond(e, 200, "{\"mailDomain\":\"sink.test\",\"maxMessageSizeBytes\":102400,\"retentionSeconds\":300}"
        .getBytes(StandardCharsets.UTF_8)));
    sink.createContext("/messages/", e -> {
      byte[] message = sinkMessages.get(e.getRequestURI().getPath().substring("/messages/".length()));
      if (message == null) respond(e, 404, new byte[0]);
      else respond(e, 200, message);
    });
    sink.start();
    sinkUrl = "http://127.0.0.1:" + sink.getAddress().getPort();
  }

  @After
  public void tearDown() {
    sink.stop(0);
    TestSends.WAIT_PER_CALL = savedWait;
    TestSends.DELIVERY_TIMEOUT = savedTimeout;
    TestSends.POLL_INTERVAL = savedPoll;
  }

  private static void respond(com.sun.net.httpserver.HttpExchange e, int status, byte[] body) throws IOException {
    e.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
    if (body.length > 0) {
      try (OutputStream out = e.getResponseBody()) {
        out.write(body);
      }
    }
    e.close();
  }

  /** "Delivers" each message to the fake sink, with the trace headers the real sink would add. */
  private class DeliveringSender implements MailSender {
    final AtomicInteger sent = new AtomicInteger();
    String lastRecipient;

    public String getFromAddress() {
      return "me@example.org";
    }

    public Session getSession() {
      return Session.getInstance(new Properties());
    }

    public void send(MimeMessage message, Address recipient) throws MessagingException {
      sent.incrementAndGet();
      lastRecipient = recipient.toString();
      String subject = message.getSubject();
      sinkMessages.put(subject, ("Return-Path: <me@example.org>\r\n"
          + "Received: from out.example.org ([203.0.113.7])\r\n"
          + "\tby sink.test (MOM SMTP sink) with ESMTPS (TLSv1.3:TLS_AES_256_GCM_SHA384)\r\n"
          + "\tfor <" + recipient + ">; Sun, 18 Oct 2026 10:00:00 +0200\r\n"
          + "Subject: " + subject + "\r\n\r\nhello\r\n").getBytes(StandardCharsets.ISO_8859_1));
    }

    public long getSentCount() {
      return sent.get();
    }
  }

  @Test
  public void sendsOncePerRunAndFetchesTheMessageBack() {
    DeliveringSender sender = new DeliveringSender();
    String account = "acct-" + UUID.randomUUID();
    String run = UUID.randomUUID().toString();

    TestSendOutcome outcome = TestSends.await(account, run, sender, sinkUrl);
    assertEquals(outcome.message(), TestSendOutcome.Kind.RECEIVED, outcome.kind());
    assertEquals("203.0.113.7", outcome.received().clientIp());
    assertEquals("out.example.org", outcome.received().helo());
    assertEquals("mom-test@sink.test", sender.lastRecipient);

    // Another check of the same run reuses it; a new run sends again.
    assertEquals(TestSendOutcome.Kind.RECEIVED, TestSends.await(account, run, sender, sinkUrl).kind());
    assertEquals(1, sender.sent.get());
    assertEquals(TestSendOutcome.Kind.RECEIVED, TestSends.await(account, UUID.randomUUID().toString(), sender, sinkUrl).kind());
    assertEquals(2, sender.sent.get());
  }

  @Test
  public void reportsPendingThenFailsWhenTheMessageNeverArrives() throws Exception {
    TestSends.WAIT_PER_CALL = Duration.ofMillis(100);
    TestSends.DELIVERY_TIMEOUT = Duration.ofMillis(600);
    MailSender lost = new DeliveringSender() {
      @Override
      public void send(MimeMessage message, Address recipient) {
        // Never reaches the sink.
      }
    };
    String account = "acct-" + UUID.randomUUID();
    String run = UUID.randomUUID().toString();
    assertEquals(TestSendOutcome.Kind.PENDING, TestSends.await(account, run, lost, sinkUrl).kind());
    Thread.sleep(900);
    TestSendOutcome outcome = TestSends.await(account, run, lost, sinkUrl);
    assertEquals(TestSendOutcome.Kind.FAILED, outcome.kind());
    assertTrue(outcome.message(), outcome.message().contains("didn't reach the SMTP sink"));
  }

  @Test
  public void reportsAnSmtpFailure() {
    MailSender refusing = new DeliveringSender() {
      @Override
      public void send(MimeMessage message, Address recipient) throws MessagingException {
        throw new MessagingException("535 Authentication failed");
      }
    };
    TestSendOutcome outcome = TestSends.await("acct-" + UUID.randomUUID(), UUID.randomUUID().toString(), refusing, sinkUrl);
    assertEquals(TestSendOutcome.Kind.FAILED, outcome.kind());
    assertTrue(outcome.message(), outcome.message().contains("535 Authentication failed"));
  }
}
