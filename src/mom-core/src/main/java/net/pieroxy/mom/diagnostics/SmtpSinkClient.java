package net.pieroxy.mom.diagnostics;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * Talks to an SMTP sink (see {@code src/smtp-sink-core}): the server MOM's mail diagnostics send
 * test messages to, through an account's own SMTP setup, and read them back from over HTTP.
 */
public final class SmtpSinkClient {
  private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(10))
      .followRedirects(HttpClient.Redirect.NORMAL)
      .build();
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
  private static final Gson GSON = new Gson();

  /** What the sink's {@code GET /info} returns. */
  public record SinkInfo(String mailDomain, int maxMessageSizeBytes, int retentionSeconds) {}

  private SmtpSinkClient() {}

  /**
   * Validates a sink URL as typed in the settings and normalizes it: trimmed, no trailing slash.
   *
   * @return null for a blank URL (diagnostics disabled).
   * @throws IllegalArgumentException if it isn't an http(s) URL with a host.
   */
  public static String normalizeUrl(String url) {
    if (url == null || url.isBlank()) return null;
    String trimmed = url.trim();
    while (trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);
    try {
      URI uri = new URI(trimmed);
      String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
      if (!scheme.equals("http") && !scheme.equals("https")) {
        throw new IllegalArgumentException("The SMTP sink URL must start with http:// or https://.");
      }
      if (uri.getHost() == null || uri.getHost().isBlank()) {
        throw new IllegalArgumentException("The SMTP sink URL has no host.");
      }
      if (uri.getQuery() != null || uri.getFragment() != null) {
        throw new IllegalArgumentException("The SMTP sink URL must not have a query or a fragment.");
      }
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("The SMTP sink URL is invalid: " + e.getMessage());
    }
    return trimmed;
  }

  /**
   * {@code GET <sinkUrl>/messages/<uuid>}: the raw message the sink received with that UUID as
   * its Subject, or empty if it has none (not arrived yet, or already expired).
   */
  public static Optional<byte[]> fetchMessage(String sinkUrl, String uuid) throws IOException, InterruptedException {
    HttpRequest request = HttpRequest.newBuilder(URI.create(sinkUrl + "/messages/" + uuid))
        .timeout(REQUEST_TIMEOUT)
        .GET()
        .build();
    HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
    if (response.statusCode() == 404) return Optional.empty();
    if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode() + " from " + request.uri());
    return Optional.of(response.body());
  }

  /** {@code GET <sinkUrl>/info}. {@code sinkUrl} must be normalized (see {@link #normalizeUrl}). */
  public static SinkInfo fetchInfo(String sinkUrl) throws IOException, InterruptedException {
    HttpRequest request = HttpRequest.newBuilder(URI.create(sinkUrl + "/info"))
        .timeout(REQUEST_TIMEOUT)
        .GET()
        .build();
    HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      throw new IOException("HTTP " + response.statusCode() + " from " + request.uri() + " — is this an SMTP sink?");
    }
    SinkInfo info;
    try {
      info = GSON.fromJson(response.body(), SinkInfo.class);
    } catch (JsonParseException e) {
      throw new IOException(request.uri() + " didn't return an SMTP sink description — is this an SMTP sink?");
    }
    if (info == null || info.mailDomain() == null || info.mailDomain().isBlank()) {
      throw new IOException(request.uri() + " didn't return an SMTP sink description — is this an SMTP sink?");
    }
    return info;
  }
}
