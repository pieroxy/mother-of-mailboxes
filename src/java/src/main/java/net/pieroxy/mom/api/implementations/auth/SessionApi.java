package net.pieroxy.mom.api.implementations.auth;

import net.pieroxy.mom.services.IServiceProvider;
import net.pieroxy.mom.api.metadata.AbstractApiEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;

/**
 * Validates a session ID the client held on to (see Auth.ts), so a page reload can silently
 * restore "logged in" state instead of bouncing back to the login page — as long as the server
 * hasn't restarted since (see SessionStore). Deliberately never requires authentication itself:
 * its whole job is to answer "is this still valid?", including when the answer is no.
 */
@Endpoint(method = ApiMethod.GET)
public class SessionApi extends AbstractApiEndpoint<SessionApiInput, SessionApiOutput> {
  private final IServiceProvider serviceProvider;

  public SessionApi(IServiceProvider serviceProvider) {
    this.serviceProvider = serviceProvider;
  }

  @Override
  public SessionApiOutput process(SessionApiInput input) {
    boolean authenticated = serviceProvider.getSessionService().isValid(input.getSessionId());
    return new SessionApiOutput(authenticated,
        authenticated && serviceProvider.getSettingsService().isWebServerPasswordTemporary());
  }
}

@TypeScriptType
class SessionApiInput {
  private String sessionId;

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }
}

@TypeScriptType
class SessionApiOutput {
  private boolean authenticated;
  private boolean passwordChangeRequired;

  public SessionApiOutput() {
  }

  public SessionApiOutput(boolean authenticated, boolean passwordChangeRequired) {
    this.authenticated = authenticated;
    this.passwordChangeRequired = passwordChangeRequired;
  }

  public boolean isAuthenticated() {
    return authenticated;
  }

  public void setAuthenticated(boolean authenticated) {
    this.authenticated = authenticated;
  }

  public boolean isPasswordChangeRequired() {
    return passwordChangeRequired;
  }

  public void setPasswordChangeRequired(boolean passwordChangeRequired) {
    this.passwordChangeRequired = passwordChangeRequired;
  }
}
