package net.pieroxy.mom.sink;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <ul>
 *   <li>{@code GET /info}: what a MOM instance needs to know to use this sink (see {@link Info}),
 *   as JSON — so that its settings only need this server's URL.</li>
 *   <li>{@code GET /messages/<uuid>}: the raw message whose Subject was that UUID, Return-Path and
 *   Received headers included (200, {@code message/rfc822}), or 404 if there's none — never
 *   received, or already expired.</li>
 * </ul>
 * Plain HTTP: TLS is the reverse proxy's job.
 */
public class HttpApi {
  private final static Logger LOGGER = Logger.getLogger(HttpApi.class.getName());
  private final static String PREFIX = "/messages/";
  private final static String INFO = "/info";

  /**
   * {@code mailDomain}: the domain to send test messages to (any local part) — the first of
   * {@code acceptedDomains}, or {@code hostname} if they're all accepted.
   */
  record Info(String mailDomain, int maxMessageSizeBytes, int retentionSeconds) {}

  private final MessageStore store;
  private final byte[] info;
  private final HttpServer server;
  private final ExecutorService executor = Executors.newFixedThreadPool(4);

  public HttpApi(SinkConfiguration config, MessageStore store) throws IOException {
    this.store = store;
    String mailDomain = config.getAcceptedDomains().isEmpty() ? config.getHostname() : config.getAcceptedDomains().get(0);
    this.info = new Gson().toJson(new Info(mailDomain, config.getMaxMessageSizeBytes(), config.getRetentionSeconds()))
        .getBytes(StandardCharsets.UTF_8);
    this.server = HttpServer.create(new InetSocketAddress(InetAddress.getByName(config.getHttpBindAddress()), config.getHttpPort()), 0);
    this.server.createContext("/", this::handle);
    this.server.setExecutor(executor);
  }

  public int getPort() {
    return server.getAddress().getPort();
  }

  public void start() {
    server.start();
    LOGGER.info("HTTP listening on " + server.getAddress().getHostString() + ":" + getPort());
  }

  public void stop() {
    server.stop(0);
    executor.shutdownNow();
  }

  private void handle(HttpExchange exchange) throws IOException {
    try (exchange) {
      exchange.getResponseHeaders().set("Cache-Control", "no-store");
      if (!exchange.getRequestMethod().equals("GET")) {
        LOGGER.info(client(exchange) + " " + exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath() + " -> 405");
        exchange.getResponseHeaders().set("Allow", "GET");
        exchange.sendResponseHeaders(405, -1);
        return;
      }
      String path = exchange.getRequestURI().getPath();
      if (path.equals(INFO)) {
        LOGGER.info(client(exchange) + " GET " + path + " -> 200");
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, info.length);
        try (OutputStream body = exchange.getResponseBody()) {
          body.write(info);
        }
        return;
      }
      Optional<byte[]> message = path.startsWith(PREFIX) ? store.read(path.substring(PREFIX.length())) : Optional.empty();
      if (message.isEmpty()) {
        LOGGER.info(client(exchange) + " GET " + path + " -> 404");
        exchange.sendResponseHeaders(404, -1);
        return;
      }
      LOGGER.info(client(exchange) + " GET " + path + " -> 200 (" + message.get().length + " bytes)");
      exchange.getResponseHeaders().set("Content-Type", "message/rfc822");
      exchange.sendResponseHeaders(200, message.get().length);
      try (OutputStream body = exchange.getResponseBody()) {
        body.write(message.get());
      }
    } catch (IOException | RuntimeException e) {
      LOGGER.log(Level.WARNING, "HTTP request failed: " + exchange.getRequestURI(), e);
      throw e;
    }
  }

  /**
   * The connecting address, plus X-Forwarded-For when there is one: behind the reverse proxy, the
   * former is always the proxy. Logged as is — the sink trusts no one with it, it's only for reading.
   */
  private static String client(HttpExchange exchange) {
    String forwarded = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
    String remote = exchange.getRemoteAddress().getAddress().getHostAddress();
    return "[" + remote + (forwarded == null ? "" : " for " + forwarded) + "]";
  }
}
