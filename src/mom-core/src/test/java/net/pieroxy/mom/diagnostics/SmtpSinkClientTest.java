package net.pieroxy.mom.diagnostics;

import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SmtpSinkClientTest {
  private HttpServer server;
  private String baseUrl;

  @Before
  public void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/sink/info", exchange -> respond(exchange, 200,
        "{\"mailDomain\":\"mail.example.org\",\"maxMessageSizeBytes\":102400,\"retentionSeconds\":300}"));
    server.createContext("/notasink/info", exchange -> respond(exchange, 200, "<html>hello</html>"));
    server.start();
    baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @After
  public void tearDown() {
    server.stop(0);
  }

  private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(bytes);
    }
  }

  @Test
  public void normalizesUrls() {
    assertNull(SmtpSinkClient.normalizeUrl(null));
    assertNull(SmtpSinkClient.normalizeUrl("  "));
    assertEquals("https://sink.example.org", SmtpSinkClient.normalizeUrl(" https://sink.example.org/ "));
    assertEquals("http://example.org:8025/sink", SmtpSinkClient.normalizeUrl("http://example.org:8025/sink//"));
  }

  @Test
  public void rejectsInvalidUrls() {
    for (String url : new String[]{"sink.example.org", "ftp://sink.example.org", "https://", "https://sink.example.org/?a=b", "https://exa mple.org"}) {
      try {
        SmtpSinkClient.normalizeUrl(url);
        fail("should have rejected " + url);
      } catch (IllegalArgumentException expected) {
        // ok
      }
    }
  }

  @Test
  public void fetchesInfo() throws Exception {
    SmtpSinkClient.SinkInfo info = SmtpSinkClient.fetchInfo(baseUrl + "/sink");
    assertEquals("mail.example.org", info.mailDomain());
    assertEquals(102400, info.maxMessageSizeBytes());
    assertEquals(300, info.retentionSeconds());
  }

  @Test
  public void reportsSomethingThatIsNotASink() throws Exception {
    for (String path : new String[]{"/notasink", "/nothing"}) {
      try {
        SmtpSinkClient.fetchInfo(baseUrl + path);
        fail("should have failed for " + path);
      } catch (IOException e) {
        assertTrue(e.getMessage(), e.getMessage().contains("is this an SMTP sink?"));
      }
    }
  }
}
