package net.pieroxy.mom.services;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * In-memory sessions, issued by LoginApi and carried by the session cookie. Not persisted: a
 * restart clears all sessions.
 */
public class SessionService implements Service {
  /** Also the session cookie's Max-Age. */
  public static final Duration LIFETIME = Duration.ofDays(30);

  private final Clock clock;
  // Session ID -> session.
  private final Map<String, Session> sessions = new ConcurrentHashMap<>();

  public SessionService() {
    this(Clock.systemUTC());
  }

  SessionService(Clock clock) {
    this.clock = clock;
  }

  /** @return a new, unguessable session ID (a {@link UUID}), valid for {@link #LIFETIME}. */
  public String create(String remoteAddr, String userAgent) {
    Instant now = clock.instant();
    sessions.values().removeIf(session -> session.isExpired(now));
    String id = UUID.randomUUID().toString();
    sessions.put(id, new Session(now, now.plus(LIFETIME), remoteAddr, userAgent));
    return id;
  }

  public boolean isValid(String sessionId) {
    return get(sessionId) != null;
  }

  /** @return the session, or null if unknown or expired. */
  public Session get(String sessionId) {
    if (sessionId == null) return null;
    Session session = sessions.get(sessionId);
    if (session == null) return null;
    if (session.isExpired(clock.instant())) {
      sessions.remove(sessionId, session);
      return null;
    }
    return session;
  }

  /** Records an API call made with this session. No-op if it's unknown or expired. */
  public void recordRequest(String sessionId, String remoteAddr, String userAgent) {
    Session session = get(sessionId);
    if (session != null) session.recordRequest(clock.instant(), remoteAddr, userAgent);
  }

  /** The unexpired sessions, most recently used first. */
  public List<Session> list() {
    Instant now = clock.instant();
    return sessions.values().stream()
        .filter(session -> !session.isExpired(now))
        .sorted(Comparator.comparing(Session::getLastRequest).reversed())
        .collect(Collectors.toList());
  }

  public Instant now() {
    return clock.instant();
  }

  public void invalidate(String sessionId) {
    if (sessionId != null) sessions.remove(sessionId);
  }

  /** @return false if no session has this {@link Session#getPublicId public ID}. */
  public boolean invalidateByPublicId(String publicId) {
    return publicId != null && sessions.values().removeIf(session -> session.getPublicId().equals(publicId));
  }

  public void invalidateAllExcept(String sessionId) {
    sessions.keySet().removeIf(id -> !id.equals(sessionId));
  }

  /**
   * A session's details, as shown to the user. {@link #getPublicId} identifies it to the web UI: the
   * session ID itself is the cookie's value, so it never leaves the server.
   */
  public static class Session {
    private final String publicId = UUID.randomUUID().toString();
    private final Instant start;
    private final Instant expiry;
    private final String loginAddress;
    private final AtomicLong requests = new AtomicLong();
    private volatile Instant lastRequest;
    private volatile String lastAddress;
    private volatile String userAgent;

    Session(Instant start, Instant expiry, String remoteAddr, String userAgent) {
      this.start = start;
      this.expiry = expiry;
      this.loginAddress = remoteAddr;
      this.lastRequest = start;
      this.lastAddress = remoteAddr;
      this.userAgent = userAgent;
    }

    boolean isExpired(Instant now) {
      return !now.isBefore(expiry);
    }

    void recordRequest(Instant now, String remoteAddr, String userAgent) {
      requests.incrementAndGet();
      lastRequest = now;
      lastAddress = remoteAddr;
      this.userAgent = userAgent;
    }

    public String getPublicId() {
      return publicId;
    }

    public Instant getStart() {
      return start;
    }

    public Instant getExpiry() {
      return expiry;
    }

    public String getLoginAddress() {
      return loginAddress;
    }

    /** API calls made with this session, the login excluded. */
    public long getRequests() {
      return requests.get();
    }

    /** The login's time until the first API call. */
    public Instant getLastRequest() {
      return lastRequest;
    }

    public String getLastAddress() {
      return lastAddress;
    }

    /** As sent with the last request (the login's until the first API call). */
    public String getUserAgent() {
      return userAgent;
    }
  }
}
