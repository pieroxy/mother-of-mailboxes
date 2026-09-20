package net.pieroxy.imf.detection.reputation;

import com.sun.net.httpserver.HttpServer;
import net.pieroxy.imf.detection.reputation.ReputationListFetcher;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ReputationListFetcherTest {

  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  private HttpServer server;

  @After
  public void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  public void fetchesALocalFileViaFileScheme() throws Exception {
    var file = tempFolder.newFile("list.txt");
    Files.writeString(file.toPath(), "1.2.3.0/24\n");

    String content = ReputationListFetcher.fetch(file.toURI().toString());

    assertEquals("1.2.3.0/24\n", content);
  }

  @Test
  public void fetchesOverHttp() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/list.txt", exchange -> {
      byte[] body = "5.6.7.0/24\n".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, body.length);
      try (OutputStream os = exchange.getResponseBody()) {
        os.write(body);
      }
    });
    server.start();

    String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/list.txt";
    String content = ReputationListFetcher.fetch(url);

    assertEquals("5.6.7.0/24\n", content);
  }

  @Test
  public void nonSuccessHttpStatusThrows() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/missing.txt", exchange -> {
      exchange.sendResponseHeaders(404, -1);
      exchange.close();
    });
    server.start();

    String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/missing.txt";
    try {
      ReputationListFetcher.fetch(url);
      fail("expected an IOException for a 404 response");
    } catch (IOException expected) {
      assertTrue(expected.getMessage().contains("404"));
    }
  }
}
