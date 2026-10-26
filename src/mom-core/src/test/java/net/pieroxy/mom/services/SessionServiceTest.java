package net.pieroxy.mom.services;

import org.junit.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class SessionServiceTest {
  private static final String ADDR = "198.51.100.4";
  private static final String AGENT = "Mozilla/5.0 (test)";

  @Test
  public void aCreatedSessionIsValid() {
    SessionService store = new SessionService();

    String sessionId = store.create(ADDR, AGENT);

    assertTrue(store.isValid(sessionId));
  }

  @Test
  public void aRandomIdIsNotValid() {
    SessionService store = new SessionService();

    assertFalse(store.isValid("not-a-real-session-id"));
  }

  @Test
  public void nullIsNeverValid() {
    SessionService store = new SessionService();

    assertFalse(store.isValid(null));
  }

  @Test
  public void invalidatingRemovesTheSession() {
    SessionService store = new SessionService();
    String sessionId = store.create(ADDR, AGENT);

    store.invalidate(sessionId);

    assertFalse(store.isValid(sessionId));
  }

  @Test
  public void invalidatingNullIsANoop() {
    SessionService store = new SessionService();
    String sessionId = store.create(ADDR, AGENT);

    store.invalidate(null);

    assertTrue("invalidate(null) must not wipe out unrelated sessions", store.isValid(sessionId));
  }

  @Test
  public void invalidatingAnUnknownSessionIsANoop() {
    SessionService store = new SessionService();

    store.invalidate("never-created");
    // No exception: that's the whole assertion.
  }

  @Test
  public void eachCreatedSessionIsDistinct() {
    SessionService store = new SessionService();
    Set<String> ids = ConcurrentHashMap.newKeySet();

    for (int i = 0; i < 1000; i++) {
      String id = store.create(ADDR, AGENT);
      assertTrue("id must be freshly generated, not reused", ids.add(id));
    }
  }

  @Test
  public void differentStoresProduceDifferentSessions() {
    SessionService a = new SessionService();
    SessionService b = new SessionService();

    assertNotEquals(a.create(ADDR, AGENT), b.create(ADDR, AGENT));
  }

  @Test
  public void aSessionExpiresAfterItsLifetime() {
    MutableClock clock = new MutableClock();
    SessionService store = new SessionService(clock);
    String sessionId = store.create(ADDR, AGENT);

    clock.advance(SessionService.LIFETIME.minusSeconds(1));
    assertTrue(store.isValid(sessionId));
    clock.advance(Duration.ofSeconds(1));
    assertFalse(store.isValid(sessionId));
  }

  @Test
  public void theLifetimeIsThirtyDays() {
    assertEquals(Duration.ofDays(30), SessionService.LIFETIME);
  }

  @Test
  public void invalidatingAllButOneKeepsOnlyThatOne() {
    SessionService store = new SessionService();
    String kept = store.create(ADDR, AGENT);
    String other = store.create(ADDR, AGENT);

    store.invalidateAllExcept(kept);

    assertTrue(store.isValid(kept));
    assertFalse(store.isValid(other));
  }

  @Test
  public void aNewSessionRecordsItsLogin() {
    MutableClock clock = new MutableClock();
    SessionService store = new SessionService(clock);
    SessionService.Session session = store.get(store.create(ADDR, AGENT));

    assertEquals(clock.instant(), session.getStart());
    assertEquals(clock.instant(), session.getLastRequest());
    assertEquals(clock.instant().plus(SessionService.LIFETIME), session.getExpiry());
    assertEquals(ADDR, session.getLoginAddress());
    assertEquals(ADDR, session.getLastAddress());
    assertEquals(AGENT, session.getUserAgent());
    assertEquals(0, session.getRequests());
  }

  @Test
  public void recordingARequestUpdatesTheSession() {
    MutableClock clock = new MutableClock();
    SessionService store = new SessionService(clock);
    String sessionId = store.create(ADDR, AGENT);

    clock.advance(Duration.ofMinutes(5));
    store.recordRequest(sessionId, "203.0.113.9", "Other agent");
    store.recordRequest(sessionId, "203.0.113.9", "Other agent");

    SessionService.Session session = store.get(sessionId);
    assertEquals(2, session.getRequests());
    assertEquals(clock.instant(), session.getLastRequest());
    assertEquals("203.0.113.9", session.getLastAddress());
    assertEquals(ADDR, session.getLoginAddress());
    assertEquals("Other agent", session.getUserAgent());
  }

  @Test
  public void thePublicIdIsNotTheSessionId() {
    SessionService store = new SessionService();
    String sessionId = store.create(ADDR, AGENT);

    assertNotEquals(sessionId, store.get(sessionId).getPublicId());
  }

  @Test
  public void listingSkipsExpiredSessionsAndPutsTheMostRecentlyUsedFirst() {
    MutableClock clock = new MutableClock();
    SessionService store = new SessionService(clock);
    String expiring = store.create(ADDR, AGENT);
    clock.advance(Duration.ofDays(1));
    String older = store.create(ADDR, AGENT);
    String newer = store.create(ADDR, AGENT);
    clock.advance(SessionService.LIFETIME.minusDays(1));
    store.recordRequest(older, ADDR, AGENT);

    List<SessionService.Session> sessions = store.list();

    assertEquals(2, sessions.size());
    assertEquals(store.get(older), sessions.get(0));
    assertEquals(store.get(newer), sessions.get(1));
    assertFalse(store.isValid(expiring));
  }

  @Test
  public void invalidatingByPublicIdRemovesOnlyThatSession() {
    SessionService store = new SessionService();
    String revoked = store.create(ADDR, AGENT);
    String kept = store.create(ADDR, AGENT);

    assertTrue(store.invalidateByPublicId(store.get(revoked).getPublicId()));

    assertFalse(store.isValid(revoked));
    assertTrue(store.isValid(kept));
    assertFalse("a session ID is not a public ID", store.invalidateByPublicId(kept));
    assertFalse(store.invalidateByPublicId(null));
  }

  private static final class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-01-01T00:00:00Z");

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { throw new UnsupportedOperationException(); }
    @Override public Instant instant() { return now; }
  }
}
