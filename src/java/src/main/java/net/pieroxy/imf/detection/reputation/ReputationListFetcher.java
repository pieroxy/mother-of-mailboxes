package net.pieroxy.imf.detection.reputation;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Fetches the raw content of a list: {@code file://} for a local file, {@code http(s)://}
 * otherwise. Nothing but the list's own content ever goes over the network — no message
 * IP/domain is ever sent, unlike a reputation API queried live.
 */
final class ReputationListFetcher {
  private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(10))
      .build();

  private ReputationListFetcher() {}

  static String fetch(String url) throws IOException, InterruptedException {
    URI uri = URI.create(url);
    if ("file".equalsIgnoreCase(uri.getScheme())) {
      return Files.readString(Path.of(uri), StandardCharsets.UTF_8);
    }
    HttpRequest request = HttpRequest.newBuilder(uri)
        .timeout(Duration.ofSeconds(30))
        .GET()
        .build();
    HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() / 100 != 2) {
      throw new IOException("Unexpected HTTP status " + response.statusCode() + " fetching " + url);
    }
    return response.body();
  }
}
