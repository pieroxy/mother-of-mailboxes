package net.pieroxy.mom.api.metadata;

/** Per-request access to the session cookie, for the endpoints that read or manage it. */
public interface ApiContext {
  /** @return the session cookie's value, or null if the request carries none. */
  String getSessionId();

  void setSessionCookie(String sessionId, boolean secure);

  void clearSessionCookie(boolean secure);
}
