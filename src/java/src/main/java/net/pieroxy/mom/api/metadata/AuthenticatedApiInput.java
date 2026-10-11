package net.pieroxy.mom.api.metadata;

/**
 * Base for every authenticated endpoint's input. {@code sessionId} is filled by
 * {@link AbstractAuthenticatedEndpoint} from the session cookie once validated; {@code transient}
 * so that Gson never reads it from the request, and the TypeScript generator leaves it out.
 */
public class AuthenticatedApiInput {
  private transient String sessionId;

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }
}
