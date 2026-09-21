package net.pieroxy.imf.api;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory set of valid session IDs, issued by LoginApi and checked by SessionApi. Never
 * persisted: a process restart clears it, so a restart always requires a fresh login — there is
 * no "remember me across a service restart" concept, and no need for one (there's only ever one
 * configured web credential, not per-user accounts, so a session carries no data of its own —
 * its mere presence and validity here *is* the authorization).
 */
public class SessionStore {
  // ConcurrentHashMap-backed set: thread-safe under concurrent API requests, no ordering needed.
  private final Set<String> sessionIds = ConcurrentHashMap.newKeySet();

  /**
   * @return a new session ID. Backed by {@link UUID#randomUUID()} (SecureRandom-derived, 122
   * bits of entropy) deliberately: this is a bearer token, not just a unique key — what matters
   * is that it can't be guessed or brute-forced, not merely that it's unique.
   */
  public String create() {
    String id = UUID.randomUUID().toString();
    sessionIds.add(id);
    return id;
  }

  public boolean isValid(String sessionId) {
    return sessionId != null && sessionIds.contains(sessionId);
  }

  public void invalidate(String sessionId) {
    if (sessionId != null) sessionIds.remove(sessionId);
  }
}
