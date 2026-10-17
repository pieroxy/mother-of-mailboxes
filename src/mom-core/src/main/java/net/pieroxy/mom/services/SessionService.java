package net.pieroxy.mom.services;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sessions, issued by LoginApi and carried by the session cookie. Not persisted: a
 * restart clears all sessions.
 */
public class SessionService implements Service {
  /** Also the session cookie's Max-Age. */
  public static final Duration LIFETIME = Duration.ofDays(30);

  private final Clock clock;
  // Session ID -> expiry.
  private final Map<String, Instant> sessions = new ConcurrentHashMap<>();

  public SessionService() {
    this(Clock.systemUTC());
  }

  SessionService(Clock clock) {
    this.clock = clock;
  }

  /** @return a new, unguessable session ID (a {@link UUID}), valid for {@link #LIFETIME}. */
  public String create() {
    Instant now = clock.instant();
    sessions.values().removeIf(expiry -> !now.isBefore(expiry));
    String id = UUID.randomUUID().toString();
    sessions.put(id, now.plus(LIFETIME));
    return id;
  }

  public boolean isValid(String sessionId) {
    if (sessionId == null) return false;
    Instant expiry = sessions.get(sessionId);
    if (expiry == null) return false;
    if (!clock.instant().isBefore(expiry)) {
      sessions.remove(sessionId, expiry);
      return false;
    }
    return true;
  }

  public void invalidate(String sessionId) {
    if (sessionId != null) sessions.remove(sessionId);
  }

  public void invalidateAllExcept(String sessionId) {
    sessions.keySet().removeIf(id -> !id.equals(sessionId));
  }
}
