package net.pieroxy.mom.api.implementations.auth;

import net.pieroxy.mom.api.metadata.ApiContext;
import net.pieroxy.mom.api.metadata.TooManyRequestsException;
import net.pieroxy.mom.config.credentials.Credential;
import net.pieroxy.mom.config.credentials.CredentialsFile;
import net.pieroxy.mom.config.credentials.PasswordHasher;
import net.pieroxy.mom.config.general.Configuration;
import net.pieroxy.mom.config.general.WebServerConfiguration;
import net.pieroxy.mom.services.AccountService;
import net.pieroxy.mom.services.ClientGuardService;
import net.pieroxy.mom.services.IServiceProvider;
import net.pieroxy.mom.services.SessionService;
import net.pieroxy.mom.services.SettingsService;
import net.pieroxy.mom.services.WebServerService;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LoginApiTest {
  private static final String ATTACKER = "203.0.113.7";
  private static final String ADMIN = "198.51.100.1";

  private final LoginApi api = loginApi();

  /** Records nothing: a successful login's cookie isn't under test here. */
  private static ApiContext from(String remoteAddr) {
    return new ApiContext() {
      @Override public String getRemoteAddr() { return remoteAddr; }
      @Override public String getSessionId() { return null; }
      @Override public void setSessionCookie(String id, boolean secure) { }
      @Override public void clearSessionCookie(boolean secure) { throw new UnsupportedOperationException(); }
    };
  }

  private static LoginApi loginApi() {
    Credential credential = new Credential();
    credential.setUsername("admin");
    credential.setPasswordHash(PasswordHasher.hash("pwd"));
    CredentialsFile credentialsFile = new CredentialsFile();
    credentialsFile.setCredentials(Map.of("web", credential));

    WebServerConfiguration webServerConfig = new WebServerConfiguration();
    webServerConfig.setEnabled(true);
    webServerConfig.setCredentials("web");
    Configuration config = new Configuration();
    config.setConfigurations(new ArrayList<>());
    config.setWebServer(webServerConfig);

    // Never written: the credential is already hashed and nothing is dangling.
    File unwritable = new File("/nonexistent-dir/unused.json");
    SettingsService settingsService = new SettingsService(config, unwritable, credentialsFile, unwritable, "/nonexistent-dir");
    settingsService.start();
    SessionService sessionService = new SessionService();
    ClientGuardService guard = new ClientGuardService();
    return new LoginApi(new IServiceProvider() {
      @Override public SettingsService getSettingsService() { return settingsService; }
      @Override public AccountService getAccountService() { throw new UnsupportedOperationException(); }
      @Override public SessionService getSessionService() { return sessionService; }
      @Override public WebServerService getWebServerService() { throw new UnsupportedOperationException(); }
      @Override public ClientGuardService getClientGuardService() { return guard; }
    });
  }

  private static LoginApiInput input(String login, String password) {
    LoginApiInput input = new LoginApiInput();
    input.setLogin(login);
    input.setPassword(password);
    return input;
  }

  private void failLoginsFrom(String client, int times) {
    for (int i = 0; i < times; i++) assertFalse(api.process(input("admin", "wrong"), from(client)).isOk());
  }

  private void assertRefusedFrom(String client) {
    try {
      api.process(input("admin", "pwd"), from(client));
      fail("expected TooManyRequestsException");
    } catch (TooManyRequestsException e) {
      assertTrue(e.getMessage(), e.getMessage().startsWith("Too many failed logins, retry in "));
      assertTrue(e.getRetryAfterSeconds() >= 1 && e.getRetryAfterSeconds() <= ClientGuardService.FAILED_LOGIN_WINDOW.toSeconds());
    }
  }

  @Test
  public void aMissingLoginIsRefusedLikeAWrongPassword() {
    assertFalse(api.process(input(null, "pwd"), from(ATTACKER)).isOk());
  }

  @Test
  public void refusesAnAddressThatFailedTooOftenEvenWithTheRightPassword() {
    failLoginsFrom(ATTACKER, ClientGuardService.MAX_FAILED_LOGINS);
    assertRefusedFrom(ATTACKER);
  }

  @Test
  public void anotherAddressIsNotLockedOutBySomeoneElsesFailures() {
    failLoginsFrom(ATTACKER, ClientGuardService.MAX_FAILED_LOGINS);
    assertTrue(api.process(input("admin", "pwd"), from(ADMIN)).isOk());
  }

  @Test
  public void successfulLoginsDoNotCountAndClearPastFailures() {
    for (int i = 0; i < 10; i++) assertTrue(api.process(input("admin", "pwd"), from(ADMIN)).isOk());
    failLoginsFrom(ADMIN, ClientGuardService.MAX_FAILED_LOGINS - 1);
    assertTrue(api.process(input("admin", "pwd"), from(ADMIN)).isOk());
    failLoginsFrom(ADMIN, ClientGuardService.MAX_FAILED_LOGINS - 1);
    assertTrue("the success reset the count", api.process(input("admin", "pwd"), from(ADMIN)).isOk());
  }
}
