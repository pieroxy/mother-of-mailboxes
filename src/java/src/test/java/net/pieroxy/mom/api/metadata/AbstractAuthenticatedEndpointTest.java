package net.pieroxy.mom.api.metadata;

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
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
    boolean called = false;

    StubEndpoint(IServiceProvider serviceProvider) {
      super(serviceProvider);
    }

    @Override
    public String processAuthenticated(StubInput input) {
      called = true;
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
    };
  }

  @Test
  public void callsProcessAuthenticatedWhenTheSessionIsValid() throws Exception {
    SessionService sessionService = new SessionService();
    String sessionId = sessionService.create();
    StubEndpoint endpoint = new StubEndpoint(serviceProviderWith(sessionService));

    StubInput input = new StubInput();
    input.setSessionId(sessionId);

    assertEquals("ok", endpoint.process(input));
    assertTrue(endpoint.called);
  }

  @Test
  public void rejectsAnInvalidSessionWithoutCallingProcessAuthenticated() {
    StubEndpoint endpoint = new StubEndpoint(serviceProviderWith(new SessionService()));

    StubInput input = new StubInput();
    input.setSessionId("not-a-real-session");

    try {
      endpoint.process(input);
      fail("expected NotAuthenticatedException");
    } catch (NotAuthenticatedException expected) {
      // expected
    } catch (Exception e) {
      fail("expected NotAuthenticatedException, got " + e);
    }
    assertFalse(endpoint.called);
  }

  @Test
  public void rejectsAMissingSessionIdWithoutCallingProcessAuthenticated() {
    StubEndpoint endpoint = new StubEndpoint(serviceProviderWith(new SessionService()));

    try {
      endpoint.process(new StubInput());
      fail("expected NotAuthenticatedException");
    } catch (NotAuthenticatedException expected) {
      // expected
    } catch (Exception e) {
      fail("expected NotAuthenticatedException, got " + e);
    }
    assertFalse(endpoint.called);
  }

  @Test
  public void rejectsAValidSessionWhileThePasswordIsTemporary() {
    SessionService sessionService = new SessionService();
    StubEndpoint endpoint = new StubEndpoint(serviceProviderWith(sessionService, settingsServiceWithWebPassword(true)));

    StubInput input = new StubInput();
    input.setSessionId(sessionService.create());

    try {
      endpoint.process(input);
      fail("expected PasswordChangeRequiredException");
    } catch (PasswordChangeRequiredException expected) {
      // expected
    } catch (Exception e) {
      fail("expected PasswordChangeRequiredException, got " + e);
    }
    assertFalse(endpoint.called);
  }

  @Test
  public void letsAnOptedInEndpointThroughWhileThePasswordIsTemporary() throws Exception {
    SessionService sessionService = new SessionService();
    StubPasswordChangeEndpoint endpoint =
        new StubPasswordChangeEndpoint(serviceProviderWith(sessionService, settingsServiceWithWebPassword(true)));

    StubInput input = new StubInput();
    input.setSessionId(sessionService.create());

    assertEquals("ok", endpoint.process(input));
  }
}
