package net.pieroxy.mom.api.metadata;

/** Per-request access to the session cookie and the client's address, for the endpoints that need them. */
public interface ApiContext {
  /** The client's address: the real one behind a trusted proxy (see {@code AccessValve}). */
  String getRemoteAddr();

  /** @return the User-Agent header, or null if the request carries none. */
  String getUserAgent();

  /** @return the session cookie's value, or null if the request carries none. */
  String getSessionId();

  void setSessionCookie(String sessionId, boolean secure);

  void clearSessionCookie(boolean secure);
}
