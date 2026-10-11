package net.pieroxy.mom.services;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.pieroxy.mom.config.credentials.Credential;
import net.pieroxy.mom.config.credentials.CredentialsFile;
import net.pieroxy.mom.config.credentials.PasswordHasher;
import net.pieroxy.mom.config.general.Configuration;
import net.pieroxy.mom.config.general.WebServerConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** {@link WebServerService#changePort}/{@link WebServerService#changeAddress} against a real embedded Tomcat bound to 127.0.0.1. */
public class WebServerServiceLiveChangesTest {
  private static final String LOOPBACK = "127.0.0.1";

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private File configFile;
  private int initialPort;
  private ServiceProvider serviceProvider;

  @Before
  public void startWebServer() throws Exception {
    initialPort = freePort();
    WebServerConfiguration webServer = new WebServerConfiguration();
    webServer.setEnabled(true);
    webServer.setHttpPort(initialPort);
    webServer.setAddress(LOOPBACK);
    webServer.setCredentials("web");

    Configuration config = new Configuration();
    config.setConfigurations(new ArrayList<>());
    config.setWebServer(webServer);

    Credential login = new Credential();
    login.setUsername("admin");
    login.setPasswordHash(PasswordHasher.hash("secret"));
    Map<String, Credential> credentials = new HashMap<>();
    credentials.put("web", login);
    CredentialsFile credentialsFile = new CredentialsFile();
    credentialsFile.setCredentials(credentials);

    String dataFolder = tmp.getRoot().getAbsolutePath();
    configFile = new File(tmp.getRoot(), "config.json");
    SettingsService settingsService = new SettingsService(config, configFile, credentialsFile, new File(tmp.getRoot(), "credentials.json"), dataFolder);
    serviceProvider = new ServiceProvider(settingsService, new AccountService(settingsService, new CopyOnWriteArrayList<>()),
        new SessionService(), new WebServerService(webServer, dataFolder));
    serviceProvider.init();
  }

  @After
  public void stopWebServer() {
    serviceProvider.destroy();
  }

  @Test
  public void listensOnTheNewPortRightAwaySavesItAndClosesTheOldOneShortlyAfter() throws Exception {
    int newPort = freePort();

    serviceProvider.getWebServerService().changePort(newPort);

    assertTrue("the new port must listen as soon as changePort returns", listening(newPort));
    assertTrue("the old port must keep listening a moment, for the response to get out", listening(initialPort));
    assertEquals(newPort, savedPort());
    long deadline = System.currentTimeMillis() + 15_000;
    while (listening(initialPort) && System.currentTimeMillis() < deadline) {
      Thread.sleep(200);
    }
    assertFalse("the old port must eventually be closed", listening(initialPort));
    assertTrue(listening(newPort));
  }

  /** Through HTTP, as the webapp does: endpoints must be able to reach the web server service. */
  @Test
  public void worksThroughTheApi() throws Exception {
    int newPort = freePort();
    String sessionCookie = login();

    JsonObject response = read(send(initialPort, "ChangeWebServerPort", "application/json", sessionCookie, "{\"port\":" + newPort + "}"));

    assertTrue(response.toString(), response.get("ok").getAsBoolean());
    assertTrue(listening(newPort));
    assertEquals(newPort, savedPort());
  }

  @Test
  public void aPortAlreadyInUseChangesNothing() throws Exception {
    try (ServerSocket taken = new ServerSocket(0, 1, InetAddress.getByName(LOOPBACK))) {
      try {
        serviceProvider.getWebServerService().changePort(taken.getLocalPort());
        fail("should have thrown");
      } catch (IllegalArgumentException expected) {
        assertTrue(expected.getMessage(), expected.getMessage().contains(String.valueOf(taken.getLocalPort())));
      }
    }

    assertTrue("the current port must still listen", listening(initialPort));
    assertEquals(initialPort, serviceProvider.getSettingsService().getConfiguration().getWebServer().getHttpPort());
    assertFalse("config.json must not have been written", configFile.exists());
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsTheCurrentPort() {
    serviceProvider.getWebServerService().changePort(initialPort);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsAnOutOfRangePort() {
    serviceProvider.getWebServerService().changePort(70_000);
  }

  @Test
  public void loginSetsAnHttpOnlySameSiteSessionCookieAndReturnsNoSessionId() throws Exception {
    HttpURLConnection connection = send(initialPort, "Login", "application/json", null, "{\"login\":\"admin\",\"password\":\"secret\"}");
    String setCookie = connection.getHeaderField("Set-Cookie");

    assertTrue(setCookie, setCookie.startsWith("mom_session="));
    assertTrue(setCookie, setCookie.contains("HttpOnly"));
    assertTrue(setCookie, setCookie.contains("SameSite=Strict"));
    assertTrue(setCookie, setCookie.contains("Max-Age=" + SessionService.LIFETIME.toSeconds()));
    assertFalse("not configured as Secure", setCookie.contains("Secure;") || setCookie.endsWith("Secure"));
    assertFalse(read(connection).getAsJsonObject("result").has("sessionId"));
  }

  @Test
  public void anAuthenticatedCallWithoutTheCookieIsRejected() throws Exception {
    HttpURLConnection connection = send(initialPort, "ChangeWebServerPort", "application/json", null, "{\"port\":" + freePort() + "}");

    assertEquals(401, connection.getResponseCode());
  }

  @Test
  public void aPostThatIsNotJsonIsRejectedEvenWithAValidSession() throws Exception {
    int newPort = freePort();
    HttpURLConnection connection = send(initialPort, "ChangeWebServerPort", "text/plain", login(), "{\"port\":" + newPort + "}");

    assertEquals(415, connection.getResponseCode());
    assertFalse(listening(newPort));
  }

  @Test
  public void changingThePasswordLogsOutEveryOtherSession() throws Exception {
    String current = login();
    String other = login();

    assertTrue(read(send(initialPort, "ChangePassword", "application/json", current, "{\"newPassword\":\"changed\"}")).get("ok").getAsBoolean());

    assertEquals(401, send(initialPort, "ChangePassword", "application/json", other, "{\"newPassword\":\"again\"}").getResponseCode());
    assertTrue("the session that changed the password stays valid",
        read(send(initialPort, "ChangePassword", "application/json", current, "{\"newPassword\":\"again\"}")).get("ok").getAsBoolean());
  }

  @Test
  public void enablingTheSecureCookieSavesItAndReissuesTheCallersCookieAsSecure() throws Exception {
    HttpURLConnection connection = send(initialPort, "ChangeWebServerSecureCookie", "application/json", login(), "{\"secureCookie\":true}");

    assertTrue(read(connection).get("ok").getAsBoolean());
    String setCookie = connection.getHeaderField("Set-Cookie");
    assertTrue(setCookie, setCookie.startsWith("mom_session=") && setCookie.contains("Secure"));
    assertTrue(savedWebServer().isSecureCookie());
  }

  @Test
  public void otherCallsDontReissueTheCookie() throws Exception {
    HttpURLConnection connection = send(initialPort, "ChangePassword", "application/json", login(), "{\"newPassword\":\"changed\"}");

    assertTrue(read(connection).get("ok").getAsBoolean());
    assertEquals(null, connection.getHeaderField("Set-Cookie"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsTheCurrentSecureCookieValue() {
    serviceProvider.getWebServerService().changeSecureCookie(false);
  }

  /** @return the session cookie, as a Cookie request header value. */
  private String login() throws IOException {
    HttpURLConnection connection = send(initialPort, "Login", "application/json", null, "{\"login\":\"admin\",\"password\":\"secret\"}");
    assertTrue(read(connection).get("ok").getAsBoolean());
    return connection.getHeaderField("Set-Cookie").split(";", 2)[0];
  }

  private static HttpURLConnection send(int port, String endpoint, String contentType, String cookie, String body) throws IOException {
    HttpURLConnection connection = (HttpURLConnection) new URL("http://" + LOOPBACK + ":" + port + "/api/" + endpoint).openConnection();
    connection.setRequestMethod("POST");
    connection.setRequestProperty("Content-Type", contentType);
    if (cookie != null) connection.setRequestProperty("Cookie", cookie);
    connection.setDoOutput(true);
    try (OutputStream out = connection.getOutputStream()) {
      out.write(body.getBytes(StandardCharsets.UTF_8));
    }
    return connection;
  }

  private static JsonObject read(HttpURLConnection connection) throws IOException {
    try (InputStreamReader in = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
      return JsonParser.parseReader(in).getAsJsonObject();
    }
  }

  /** "localhost" resolves to the address already in use: forces the delayed switch-over path. */
  @Test
  public void switchesToAnAddressThatCantListenAlongsideTheCurrentOne() throws Exception {
    serviceProvider.getWebServerService().changeAddress("localhost");

    long deadline = System.currentTimeMillis() + 10_000;
    while (!"localhost".equals(savedWebServer().getAddress()) && System.currentTimeMillis() < deadline) {
      Thread.sleep(100);
    }
    assertEquals("localhost", savedWebServer().getAddress());
    assertTrue(listening(initialPort));
    assertEquals(null, serviceProvider.getWebServerService().getAddressChangeError());
  }

  @Test
  public void rejectsAnAddressThatIsNotThisMachines() {
    try {
      serviceProvider.getWebServerService().changeAddress("192.0.2.1"); // TEST-NET-1, never assigned
      fail("should have thrown");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage(), expected.getMessage().contains("not an address of this machine"));
    }
    assertTrue(listening(initialPort));
    assertFalse("config.json must not have been written", configFile.exists());
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsTheCurrentAddress() {
    serviceProvider.getWebServerService().changeAddress(" " + LOOPBACK + " ");
  }

  @Test
  public void disablingSavesEnabledFalseKeepsTheRestAndStopsListeningShortlyAfter() throws Exception {
    serviceProvider.getWebServerService().disable();

    WebServerConfiguration saved = savedWebServer();
    assertFalse(saved.isEnabled());
    assertEquals(initialPort, saved.getHttpPort());
    assertEquals(LOOPBACK, saved.getAddress());
    assertEquals("web", saved.getCredentials());
    long deadline = System.currentTimeMillis() + 10_000;
    while (listening(initialPort) && System.currentTimeMillis() < deadline) {
      Thread.sleep(100);
    }
    assertFalse("Tomcat must be stopped", listening(initialPort));
    try {
      serviceProvider.getWebServerService().changePort(freePort());
      fail("a disabled web server must refuse any further change");
    } catch (IllegalStateException expected) {
      // ok
    }
  }

  private int savedPort() throws IOException {
    return savedWebServer().getHttpPort();
  }

  private WebServerConfiguration savedWebServer() throws IOException {
    if (!configFile.exists()) return new WebServerConfiguration();
    try (FileReader reader = new FileReader(configFile)) {
      return new Gson().fromJson(reader, Configuration.class).getWebServer();
    }
  }

  private static int freePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName(LOOPBACK))) {
      return socket.getLocalPort();
    }
  }

  private static boolean listening(int port) {
    try (Socket ignored = new Socket(LOOPBACK, port)) {
      return true;
    } catch (IOException e) {
      return false;
    }
  }
}
