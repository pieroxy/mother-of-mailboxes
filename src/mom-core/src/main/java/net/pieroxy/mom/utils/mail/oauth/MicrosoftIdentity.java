package net.pieroxy.mom.utils.mail.oauth;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The Microsoft identity platform's OAuth 2.0 endpoints for a public client (no secret): the
 * device code flow to sign in, and refresh tokens. The {@code common} tenant takes both personal
 * (Outlook.com, Hotmail) and work or school accounts.
 */
public class MicrosoftIdentity {
  /** IMAP and SMTP share the outlook.office.com resource, so one token covers both. openid and profile get the account's address. */
  static final String SCOPES = "https://outlook.office.com/IMAP.AccessAsUser.All https://outlook.office.com/SMTP.Send offline_access openid profile";
  private static final Duration TIMEOUT = Duration.ofSeconds(20);
  /** MOM's own app registration, multi-tenant and open to personal accounts. Public: a public client has no secret. */
  public static final String MOM_CLIENT_ID = "bf65ae8d-fb53-4b80-afc4-1fc922e96f70";
  public static final MicrosoftIdentity DEFAULT = new MicrosoftIdentity(URI.create("https://login.microsoftonline.com/common/oauth2/v2.0/"));

  private final URI baseUri;
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

  /** @param baseUri ends with a slash. */
  public MicrosoftIdentity(URI baseUri) {
    this.baseUri = baseUri;
  }

  /** @param verificationUri where the user types {@code userCode}. */
  public record DeviceCode(String deviceCode, String userCode, String verificationUri, int expiresInSeconds, int intervalSeconds) {
  }

  /**
   * @param refreshToken null if the provider didn't issue a new one.
   * @param username the signed-in account's address, from the ID token; null without one.
   */
  public record Tokens(String accessToken, String refreshToken, int expiresInSeconds, String username) {
  }

  /** An error answer, e.g. {@code authorization_pending} or {@code invalid_grant}. */
  public static class OAuthException extends Exception {
    private final String error;

    public OAuthException(String error, String description) {
      super(description != null && !description.isBlank() ? firstLine(description) : error);
      this.error = error;
    }

    public String getError() {
      return error;
    }

    // Descriptions go on with trace and correlation IDs on later lines.
    private static String firstLine(String description) {
      return description.lines().findFirst().orElse(description).trim();
    }
  }

  public DeviceCode requestDeviceCode(String clientId) throws IOException, OAuthException {
    JsonObject json = post("devicecode", Map.of("client_id", clientId, "scope", SCOPES));
    return new DeviceCode(string(json, "device_code"), string(json, "user_code"), string(json, "verification_uri"),
        integer(json, "expires_in", 900), integer(json, "interval", 5));
  }

  /** @throws OAuthException with {@code authorization_pending} until the user is done signing in. */
  public Tokens redeemDeviceCode(String clientId, String deviceCode) throws IOException, OAuthException {
    return tokens(post("token", Map.of(
        "grant_type", "urn:ietf:params:oauth:grant-type:device_code", "client_id", clientId, "device_code", deviceCode)));
  }

  /** @throws OAuthException with {@code invalid_grant} when the sign-in was revoked or expired: only signing in again helps. */
  public Tokens refresh(String clientId, String refreshToken) throws IOException, OAuthException {
    return tokens(post("token", Map.of(
        "grant_type", "refresh_token", "client_id", clientId, "refresh_token", refreshToken, "scope", SCOPES)));
  }

  private JsonObject post(String endpoint, Map<String, String> form) throws IOException, OAuthException {
    String body = form.entrySet().stream()
        .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
        .collect(Collectors.joining("&"));
    HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(endpoint))
        .timeout(TIMEOUT)
        .header("Content-Type", "application/x-www-form-urlencoded")
        .header("Accept", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();
    HttpResponse<String> response;
    try {
      response = http.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while calling Microsoft", e);
    }
    JsonObject json;
    try {
      JsonElement parsed = JsonParser.parseString(response.body());
      json = parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
    } catch (JsonParseException e) {
      json = null;
    }
    if (json == null) throw new IOException("Microsoft answered HTTP " + response.statusCode() + " without JSON");
    if (json.has("error")) throw new OAuthException(string(json, "error"), string(json, "error_description"));
    if (response.statusCode() != 200) throw new IOException("Microsoft answered HTTP " + response.statusCode());
    return json;
  }

  private static Tokens tokens(JsonObject json) throws IOException {
    String accessToken = string(json, "access_token");
    if (accessToken == null) throw new IOException("Microsoft's answer has no access token");
    return new Tokens(accessToken, string(json, "refresh_token"), integer(json, "expires_in", 3600), usernameOf(string(json, "id_token")));
  }

  /** The ID token's {@code preferred_username}. Not verified: it only pre-fills the username, which the IMAP login then checks. */
  static String usernameOf(String idToken) {
    if (idToken == null) return null;
    String[] parts = idToken.split("\\.");
    if (parts.length < 2) return null;
    try {
      JsonElement claims = JsonParser.parseString(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
      return claims.isJsonObject() ? string(claims.getAsJsonObject(), "preferred_username") : null;
    } catch (IllegalArgumentException | JsonParseException e) {
      return null;
    }
  }

  private static String string(JsonObject json, String name) {
    JsonElement value = json.get(name);
    return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
  }

  private static int integer(JsonObject json, String name, int fallback) {
    JsonElement value = json.get(name);
    try {
      return value != null && value.isJsonPrimitive() ? value.getAsInt() : fallback;
    } catch (NumberFormatException e) {
      return fallback;
    }
  }
}
