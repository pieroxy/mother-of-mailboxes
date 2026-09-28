package net.pieroxy.mom.services;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory set of valid session IDs, issued by LoginApi and checked by SessionApi. Not
 * persisted: a restart clears all sessions.
 */
public class SessionService implements Service {
  // ConcurrentHashMap-backed set: thread-safe under concurrent API requests, no ordering needed.
  private final Set<String> sessionIds = ConcurrentHashMap.newKeySet();

  /** @return a new, unguessable session ID (a {@link UUID}). */
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
