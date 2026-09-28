package net.pieroxy.mom.api.metadata;

/**
 * Base for every authenticated endpoint's input: carries the session ID the frontend attaches to
 * every request (see Api.ts), checked by {@link AbstractAuthenticatedEndpoint} before the
 * endpoint's own logic ever runs. Not itself {@link TypeScriptType}-annotated — the generator
 * (Gson-backed, so field-based) still picks up this inherited field on every concrete subclass.
 */
public class AuthenticatedApiInput {
  private String sessionId;

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }
}
