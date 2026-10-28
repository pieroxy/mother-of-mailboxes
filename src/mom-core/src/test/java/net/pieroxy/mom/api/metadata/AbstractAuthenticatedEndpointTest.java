package net.pieroxy.mom.api.metadata;

import net.pieroxy.mom.config.credentials.Credential;
import net.pieroxy.mom.config.credentials.CredentialsFile;
import net.pieroxy.mom.config.credentials.PasswordHasher;
import net.pieroxy.mom.config.general.Configuration;
import net.pieroxy.mom.config.general.WebServerConfiguration;
import net.pieroxy.mom.services.AccountService;
import net.pieroxy.mom.services.ClientGuardService;
import net.pieroxy.mom.services.OAuthSignInService;
import net.pieroxy.mom.services.IServiceProvider;
import net.pieroxy.mom.services.SessionService;
import net.pieroxy.mom.services.SettingsService;
import net.pieroxy.mom.services.WebServerService;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/**
 * Exercises the session check itself, and — since {@code StubEndpoint} extends
 * {@link AbstractAuthenticatedEndpoint} rather than {@link AbstractApiEndpoint} directly — also
 * doubles as a regression test for {@link AbstractApiEndpoint#resolveInputType()}'s handling of an
 * intermediate generic superclass (the reflection-based walk this whole mechanism depends on to
 * even find {@code StubInput} at construction time).
 */
public class AbstractAuthenticatedEndpointTest {
  private static final class StubInput extends AuthenticatedApiInput {
  }

  private static final class StubEndpoint extends AbstractAuthenticatedEndpoint<StubInput, String> {
    StubInput received;

    StubEndpoint(IServiceProvider serviceProvider) {
      super(serviceProvider);
    }

    @Override
    public String processAuthenticated(StubInput input) {
      received = input;
      return "ok";
    }
  }

  private static final class StubPasswordChangeEndpoint extends AbstractAuthenticatedEndpoint<StubInput, String> {
    StubPasswordChangeEndpoint(IServiceProvider serviceProvider) {
      super(serviceProvider);
    }

    @Override
    protected boolean allowedWithTemporaryPassword() {
      return true;
    }

    @Override
    public String processAuthenticated(StubInput input) {
      return "ok";
    }
  }

  private static ApiContext contextWith(String sessionId) {
    return new ApiContext() {
      @Override public String getRemoteAddr() { return "127.0.0.1"; }
      @Override public String getUserAgent() { return "test-agent"; }
      @Override public String getSessionId() { return sessionId; }
      @Override public void setSessionCookie(String id, boolean secure) { throw new UnsupportedOperationException(); }
      @Override public void clearSessionCookie(boolean secure) { throw new UnsupportedOperationException(); }
    };
  }

  private static SettingsService settingsServiceWithWebPassword(boolean temporary) {
    Credential credential = new Credential();
    credential.setUsername("admin");
    credential.setPasswordHash(PasswordHasher.hash("pwd"));
    credential.setTemporary(temporary);
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
    return settingsService;
  }

  private static IServiceProvider serviceProviderWith(SessionService sessionService) {
    return serviceProviderWith(sessionService, settingsServiceWithWebPassword(false));
  }

  private static IServiceProvider serviceProviderWith(SessionService sessionService, SettingsService settingsService) {
    return new IServiceProvider() {
      @Override public SettingsService getSettingsService() { return settingsService; }
      @Override public AccountService getAccountService() { throw new UnsupportedOperationException(); }
      @Override public SessionService getSessionService() { return sessionService; }
      @Override public WebServerService getWebServerService() { throw new UnsupportedOperationException(); }
      @Override public ClientGuardService getClientGuardService() { throw new UnsupportedOperationException(); }
      @Override public OAuthSignInService getOAuthSignInService() { throw new UnsupportedOperationException(); }
    };
  }

  @Test
  public void callsProcessAuthenticatedWhenTheSessionIsValid() throws Exception {
    SessionService sessionService = new SessionService();
    String sessionId = sessionService.create("127.0.0.1", "test-agent");
    StubEndpoint endpoint = new StubEndpoint(serviceProviderWith(sessionService));

    assertEquals("ok", endpoint.process(new StubInput(), contextWith(sessionId)));
    assertEquals("the validated session ID is handed to the endpoint", sessionId, endpoint.received.getSessionId());
  }

  @Test
  public void rejectsAnInvalidSessionWithoutCallingProcessAuthenticated() {
    StubEndpoint endpoint = new StubEndpoint(serviceProviderWith(new SessionService()));

    try {
      endpoint.process(new StubInput(), contextWith("not-a-real-session"));
      fail("expected NotAuthenticatedException");
    } catch (NotAuthenticatedException expected) {
      // expected
    } catch (Exception e) {
      fail("expected NotAuthenticatedException, got " + e);
    }
    assertNull(endpoint.received);
  }

  @Test
  public void rejectsAMissingSessionIdWithoutCallingProcessAuthenticated() {
    StubEndpoint endpoint = new StubEndpoint(serviceProviderWith(new SessionService()));

    try {
      endpoint.process(new StubInput(), contextWith(null));
      fail("expected NotAuthenticatedException");
    } catch (NotAuthenticatedException expected) {
      // expected
    } catch (Exception e) {
      fail("expected NotAuthenticatedException, got " + e);
    }
    assertNull(endpoint.received);
  }

  @Test
  public void rejectsAValidSessionWhileThePasswordIsTemporary() {
    SessionService sessionService = new SessionService();
    StubEndpoint endpoint = new StubEndpoint(serviceProviderWith(sessionService, settingsServiceWithWebPassword(true)));

    try {
      endpoint.process(new StubInput(), contextWith(sessionService.create("127.0.0.1", "test-agent")));
      fail("expected PasswordChangeRequiredException");
    } catch (PasswordChangeRequiredException expected) {
      // expected
    } catch (Exception e) {
      fail("expected PasswordChangeRequiredException, got " + e);
    }
    assertNull(endpoint.received);
  }

  @Test
  public void letsAnOptedInEndpointThroughWhileThePasswordIsTemporary() throws Exception {
    SessionService sessionService = new SessionService();
    StubPasswordChangeEndpoint endpoint =
        new StubPasswordChangeEndpoint(serviceProviderWith(sessionService, settingsServiceWithWebPassword(true)));

    assertEquals("ok", endpoint.process(new StubInput(), contextWith(sessionService.create("127.0.0.1", "test-agent"))));
  }
}
