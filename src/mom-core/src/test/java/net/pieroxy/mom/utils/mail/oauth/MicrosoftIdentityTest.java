package net.pieroxy.mom.utils.mail.oauth;

import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class MicrosoftIdentityTest {
  private HttpServer server;
  private MicrosoftIdentity identity;
  private final AtomicReference<String> lastPath = new AtomicReference<>();
  private final AtomicReference<String> lastBody = new AtomicReference<>();
  private volatile int status = 200;
  private volatile String answer = "{}";

  @Before
  public void startServer() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      lastPath.set(exchange.getRequestURI().getPath());
      lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      byte[] bytes = answer.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(status, bytes.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(bytes);
      }
    });
    server.start();
    identity = new MicrosoftIdentity(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/common/oauth2/v2.0/"));
  }

  @After
  public void stopServer() {
    server.stop(0);
  }

  @Test
  public void requestsADeviceCodeForImapAndSmtp() throws Exception {
    answer = "{\"device_code\":\"dc\",\"user_code\":\"ABCD-EFGH\",\"verification_uri\":\"https://microsoft.com/devicelogin\","
        + "\"expires_in\":900,\"interval\":5}";

    MicrosoftIdentity.DeviceCode code = identity.requestDeviceCode("my-client");

    assertEquals("/common/oauth2/v2.0/devicecode", lastPath.get());
    assertTrue(lastBody.get().contains("client_id=my-client"));
    assertTrue(lastBody.get().contains("IMAP.AccessAsUser.All"));
    assertTrue(lastBody.get().contains("SMTP.Send"));
    assertTrue(lastBody.get().contains("offline_access"));
    assertEquals("dc", code.deviceCode());
    assertEquals("ABCD-EFGH", code.userCode());
    assertEquals("https://microsoft.com/devicelogin", code.verificationUri());
    assertEquals(900, code.expiresInSeconds());
    assertEquals(5, code.intervalSeconds());
  }

  @Test
  public void redeemsADeviceCodeAndReadsTheUsernameFromTheIdToken() throws Exception {
    String claims = Base64.getUrlEncoder().withoutPadding()
        .encodeToString("{\"preferred_username\":\"me@outlook.com\"}".getBytes(StandardCharsets.UTF_8));
    answer = "{\"access_token\":\"at\",\"refresh_token\":\"rt\",\"expires_in\":3599,\"id_token\":\"header." + claims + ".signature\"}";

    MicrosoftIdentity.Tokens tokens = identity.redeemDeviceCode("my-client", "dc");

    assertEquals("/common/oauth2/v2.0/token", lastPath.get());
    assertTrue(lastBody.get().contains("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Adevice_code"));
    assertTrue(lastBody.get().contains("device_code=dc"));
    assertEquals(new MicrosoftIdentity.Tokens("at", "rt", 3599, "me@outlook.com"), tokens);
  }

  @Test
  public void anErrorAnswerKeepsItsCodeAndTheFirstLineOfItsDescription() {
    status = 400;
    answer = "{\"error\":\"invalid_grant\",\"error_description\":\"AADSTS70000: The grant is expired.\\r\\nTrace ID: 123\"}";

    MicrosoftIdentity.OAuthException e = assertThrows(MicrosoftIdentity.OAuthException.class, () -> identity.refresh("my-client", "rt"));

    assertEquals("invalid_grant", e.getError());
    assertEquals("AADSTS70000: The grant is expired.", e.getMessage());
    assertTrue(lastBody.get().contains("grant_type=refresh_token"));
  }

  @Test
  public void aRefreshWithoutNewRefreshTokenOrIdTokenHasNeither() throws Exception {
    answer = "{\"access_token\":\"at\",\"expires_in\":3599}";

    MicrosoftIdentity.Tokens tokens = identity.refresh("my-client", "rt");

    assertEquals("at", tokens.accessToken());
    assertNull(tokens.refreshToken());
    assertNull(tokens.username());
  }

  @Test
  public void aNonJsonAnswerIsAnIoFailure() {
    status = 502;
    answer = "<html>Bad gateway</html>";

    assertThrows(java.io.IOException.class, () -> identity.refresh("my-client", "rt"));
  }
}
