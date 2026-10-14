package net.pieroxy.mom.api.implementations.auth;

import net.pieroxy.mom.api.metadata.ApiContext;
import net.pieroxy.mom.api.metadata.TooManyRequestsException;
import net.pieroxy.mom.config.credentials.Credential;
import net.pieroxy.mom.config.credentials.CredentialsFile;
import net.pieroxy.mom.config.credentials.PasswordHasher;
import net.pieroxy.mom.config.general.Configuration;
import net.pieroxy.mom.config.general.WebServerConfiguration;
import net.pieroxy.mom.services.AccountService;
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
  private static final ApiContext NO_COOKIE = new ApiContext() {
    @Override public String getSessionId() { return null; }
    @Override public void setSessionCookie(String id, boolean secure) { throw new UnsupportedOperationException(); }
    @Override public void clearSessionCookie(boolean secure) { throw new UnsupportedOperationException(); }
  };

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
    return new LoginApi(new IServiceProvider() {
      @Override public SettingsService getSettingsService() { return settingsService; }
      @Override public AccountService getAccountService() { throw new UnsupportedOperationException(); }
      @Override public SessionService getSessionService() { throw new UnsupportedOperationException(); }
      @Override public WebServerService getWebServerService() { throw new UnsupportedOperationException(); }
    });
  }

  private static LoginApiInput input(String login, String password) {
    LoginApiInput input = new LoginApiInput();
    input.setLogin(login);
    input.setPassword(password);
    return input;
  }

  @Test
  public void aMissingLoginIsRefusedLikeAWrongPassword() {
    assertFalse(loginApi().process(input(null, "pwd"), NO_COOKIE).isOk());
  }

  @Test
  public void refusesWithARetryDelayOnceTheRateIsExceeded() {
    LoginApi api = loginApi();
    assertFalse(api.process(input("admin", "wrong"), NO_COOKIE).isOk());
    assertFalse(api.process(input("admin", "wrong"), NO_COOKIE).isOk());
    try {
      api.process(input("admin", "wrong"), NO_COOKIE);
      fail("expected TooManyRequestsException");
    } catch (TooManyRequestsException e) {
      assertTrue(e.getMessage(), e.getMessage().startsWith("Too many login attempts, retry in "));
      assertTrue(e.getRetryAfterSeconds() >= 1 && e.getRetryAfterSeconds() <= 10);
    }
  }
}
