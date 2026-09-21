package net.pieroxy.imf.api.implementations.auth;

import net.pieroxy.imf.api.ServiceProvider;
import net.pieroxy.imf.api.metadata.AbstractApiEndpoint;
import net.pieroxy.imf.api.metadata.ApiMethod;
import net.pieroxy.imf.api.metadata.Endpoint;
import net.pieroxy.imf.api.metadata.TypeScriptType;

/**
 * Validates a session ID the client held on to (see Auth.ts), so a page reload can silently
 * restore "logged in" state instead of bouncing back to the login page — as long as the server
 * hasn't restarted since (see SessionStore). Deliberately never requires authentication itself:
 * its whole job is to answer "is this still valid?", including when the answer is no.
 */
@Endpoint(method = ApiMethod.GET)
public class SessionApi extends AbstractApiEndpoint<SessionApiInput, SessionApiOutput> {
  private final ServiceProvider serviceProvider;

  public SessionApi(ServiceProvider serviceProvider) {
    this.serviceProvider = serviceProvider;
  }

  @Override
  public SessionApiOutput process(SessionApiInput input) {
    return new SessionApiOutput(serviceProvider.getSessionStore().isValid(input.getSessionId()));
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

  public SessionApiOutput() {
  }

  public SessionApiOutput(boolean authenticated) {
    this.authenticated = authenticated;
  }

  public boolean isAuthenticated() {
    return authenticated;
  }

  public void setAuthenticated(boolean authenticated) {
    this.authenticated = authenticated;
  }
}
