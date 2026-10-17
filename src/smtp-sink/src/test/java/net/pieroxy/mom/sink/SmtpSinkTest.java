package net.pieroxy.mom.sink;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** End-to-end: a real SMTP conversation over a socket, then the message fetched over HTTP. */
public class SmtpSinkTest {
  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private SmtpSink sink;

  @Before
  public void setUp() throws Exception {
    start(false, 300);
  }

  @After
  public void tearDown() {
    if (sink != null) sink.stop();
  }

  private void start(boolean proxyProtocol, int retentionSeconds) throws Exception {
    if (sink != null) sink.stop();
    Path dir = tmp.newFolder().toPath();
    Files.copy(getClass().getResourceAsStream("/test-cert.pem"), dir.resolve("cert.pem"));
    Files.copy(getClass().getResourceAsStream("/test-key.pem"), dir.resolve("key.pem"));
    Files.writeString(dir.resolve("config.json"), "{"
        + "\"hostname\": \"sink.test\","
        + "\"smtpBindAddress\": \"127.0.0.1\", \"smtpPort\": 0, \"httpPort\": 0,"
        + "\"smtpProxyProtocol\": " + proxyProtocol + ","
        + "\"acceptedDomains\": [\"Sink.Test\"],"
        + "\"maxMessageSizeBytes\": 1000,"
        + "\"retentionSeconds\": " + retentionSeconds + ","
        + "\"tls\": {\"certFile\": \"cert.pem\", \"keyFile\": \"key.pem\"}"
        + "}");
    sink = new SmtpSink(SmtpSink.loadConfiguration(dir.toFile()));
    sink.start();
  }

  @Test
  public void storesMessageWithUuidSubjectAndServesIt() throws Exception {
    String uuid = UUID.randomUUID().toString();
    try (Client c = new Client()) {
      c.expect("220");
      c.send("EHLO client.example");
      List<String> ehlo = c.readMulti();
      assertTrue(ehlo.contains("250-SIZE 1000"));
      assertTrue(ehlo.contains("250-STARTTLS") || ehlo.contains("250 STARTTLS"));
      c.send("MAIL FROM:<sender@example.org> SIZE=200");
      c.expect("250");
      c.send("RCPT TO:<test@sink.test>");
      c.expect("250");
      c.send("DATA");
      c.expect("354");
      c.send("From: sender@example.org\r\nSubject: " + uuid.toUpperCase() + "\r\n\r\n..leading dot\r\nbody\r\n.");
      c.expect("250");
      c.send("QUIT");
      c.expect("221");
    }

    Response r = get("/messages/" + uuid);
    assertEquals(200, r.status);
    assertTrue(r.body, r.body.startsWith("Return-Path: <sender@example.org>\r\nReceived: from client.example ([127.0.0.1])\r\n\tby sink.test (MOM SMTP sink) with ESMTP\r\n\tfor <test@sink.test>; "));
    assertTrue(r.body, r.body.endsWith("\r\n\r\n.leading dot\r\nbody\r\n"));
  }

  @Test
  public void refusesNonUuidSubject() throws Exception {
    try (Client c = new Client()) {
      c.expect("220");
      c.send("HELO client.example");
      c.expect("250");
      c.send("MAIL FROM:<>");
      c.expect("250");
      c.send("RCPT TO:<test@sink.test>");
      c.expect("250");
      c.send("DATA");
      c.expect("354");
      c.send("Subject: Confirm your account\r\n\r\nhello\r\n.");
      c.expect("550");
    }
    assertEquals(0, countEml());
  }

  @Test
  public void refusesOtherDomains() throws Exception {
    try (Client c = new Client()) {
      c.expect("220");
      c.send("EHLO client.example");
      c.readMulti();
      c.send("MAIL FROM:<a@example.org>");
      c.expect("250");
      c.send("RCPT TO:<victim@elsewhere.example>");
      c.expect("550");
    }
  }

  @Test
  public void refusesTooBigMessages() throws Exception {
    try (Client c = new Client()) {
      c.expect("220");
      c.send("EHLO client.example");
      c.readMulti();
      c.send("MAIL FROM:<a@example.org> SIZE=5000");
      c.expect("552");
      c.send("MAIL FROM:<a@example.org>");
      c.expect("250");
      c.send("RCPT TO:<test@sink.test>");
      c.expect("250");
      c.send("DATA");
      c.expect("354");
      c.send("Subject: " + UUID.randomUUID() + "\r\n\r\n" + "x".repeat(2000) + "\r\n.");
      c.expect("552");
      // The session is still usable afterwards.
      c.send("NOOP");
      c.expect("250");
    }
    assertEquals(0, countEml());
  }

  @Test
  public void startTlsIsRecorded() throws Exception {
    String uuid = UUID.randomUUID().toString();
    try (Client c = new Client()) {
      c.expect("220");
      c.send("EHLO client.example");
      c.readMulti();
      c.send("STARTTLS");
      c.expect("220");
      c.upgradeToTls();
      c.send("MAIL FROM:<a@example.org>");
      c.expect("503"); // EHLO is forgotten after STARTTLS.
      c.send("EHLO client.example");
      List<String> ehlo = c.readMulti();
      assertFalse(ehlo.toString(), ehlo.toString().contains("STARTTLS"));
      c.send("MAIL FROM:<a@example.org>");
      c.expect("250");
      c.send("RCPT TO:<test@sink.test>");
      c.expect("250");
      c.send("DATA");
      c.expect("354");
      c.send("Subject: " + uuid + "\r\n\r\nhello\r\n.");
      c.expect("250");
    }
    Response r = get("/messages/" + uuid);
    assertEquals(200, r.status);
    assertTrue(r.body, r.body.contains("with ESMTPS (TLSv1."));
  }

  @Test
  public void proxyProtocolProvidesClientIp() throws Exception {
    start(true, 300);
    String uuid = UUID.randomUUID().toString();
    try (Client c = new Client()) {
      c.send("PROXY TCP6 2001:db8::7 2001:db8::1 51234 25");
      c.expect("220");
      c.send("EHLO client.example");
      c.readMulti();
      c.send("MAIL FROM:<a@example.org>");
      c.expect("250");
      c.send("RCPT TO:<test@sink.test>");
      c.expect("250");
      c.send("DATA");
      c.expect("354");
      c.send("Subject: " + uuid + "\r\n\r\nhello\r\n.");
      c.expect("250");
    }
    assertTrue(get("/messages/" + uuid).body.contains("Received: from client.example ([IPv6:2001:db8::7])"));
  }

  @Test
  public void expiredMessagesAreGone() throws Exception {
    start(false, 1);
    String uuid = UUID.randomUUID().toString();
    try (Client c = new Client()) {
      c.expect("220");
      c.send("HELO client.example");
      c.expect("250");
      c.send("MAIL FROM:<a@example.org>");
      c.expect("250");
      c.send("RCPT TO:<test@sink.test>");
      c.expect("250");
      c.send("DATA");
      c.expect("354");
      c.send("Subject: " + uuid + "\r\n\r\nhello\r\n.");
      c.expect("250");
    }
    assertEquals(200, get("/messages/" + uuid).status);
    Thread.sleep(1100);
    assertEquals(404, get("/messages/" + uuid).status);
  }

  @Test
  public void httpStatuses() throws Exception {
    assertEquals(404, get("/messages/" + UUID.randomUUID()).status);
    assertEquals(404, get("/messages/not-a-uuid").status);
    assertEquals(404, get("/").status);
    HttpURLConnection conn = (HttpURLConnection) new URL("http://127.0.0.1:" + sink.getHttpPort() + "/messages/x").openConnection();
    conn.setRequestMethod("POST");
    assertEquals(405, conn.getResponseCode());
  }

  private long countEml() throws IOException {
    try (var files = Files.walk(tmp.getRoot().toPath())) {
      return files.filter(p -> p.toString().endsWith(".eml")).count();
    }
  }

  private record Response(int status, String body) {}

  private Response get(String path) throws IOException {
    HttpURLConnection conn = (HttpURLConnection) new URL("http://127.0.0.1:" + sink.getHttpPort() + path).openConnection();
    int status = conn.getResponseCode();
    if (status != 200) return new Response(status, "");
    try (InputStream in = conn.getInputStream()) {
      return new Response(status, new String(in.readAllBytes(), StandardCharsets.UTF_8));
    }
  }

  /** A bare-bones SMTP client: one command or reply line at a time. */
  private class Client implements AutoCloseable {
    private Socket socket;
    private BufferedReader in;
    private OutputStream out;

    Client() throws IOException {
      socket = new Socket("127.0.0.1", sink.getSmtpPort());
      socket.setSoTimeout(5000);
      in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
      out = socket.getOutputStream();
    }

    void send(String line) throws IOException {
      out.write((line + "\r\n").getBytes(StandardCharsets.UTF_8));
      out.flush();
    }

    void expect(String code) throws IOException {
      String line = in.readLine();
      assertTrue("Expected " + code + ", got: " + line, line != null && line.startsWith(code + " "));
    }

    List<String> readMulti() throws IOException {
      List<String> lines = new java.util.ArrayList<>();
      String line;
      do {
        line = in.readLine();
        lines.add(line);
      } while (line != null && line.length() > 3 && line.charAt(3) == '-');
      return lines;
    }

    void upgradeToTls() throws Exception {
      SSLContext ctx = SSLContext.getInstance("TLS");
      ctx.init(null, new TrustManager[]{new X509TrustManager() {
        public void checkClientTrusted(X509Certificate[] chain, String authType) {}
        public void checkServerTrusted(X509Certificate[] chain, String authType) {}
        public X509Certificate[] getAcceptedIssuers() {
          return new X509Certificate[0];
        }
      }}, null);
      SSLSocket ssl = (SSLSocket) ctx.getSocketFactory().createSocket(socket, "localhost", socket.getPort(), true);
      ssl.startHandshake();
      socket = ssl;
      in = new BufferedReader(new InputStreamReader(ssl.getInputStream(), StandardCharsets.UTF_8));
      out = ssl.getOutputStream();
    }

    @Override
    public void close() throws IOException {
      socket.close();
    }
  }
}
