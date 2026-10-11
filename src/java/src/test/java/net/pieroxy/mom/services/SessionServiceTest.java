package net.pieroxy.mom.services;

import org.junit.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class SessionServiceTest {
  @Test
  public void aCreatedSessionIsValid() {
    SessionService store = new SessionService();

    String sessionId = store.create();

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
    String sessionId = store.create();

    store.invalidate(sessionId);

    assertFalse(store.isValid(sessionId));
  }

  @Test
  public void invalidatingNullIsANoop() {
    SessionService store = new SessionService();
    String sessionId = store.create();

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
      String id = store.create();
      assertTrue("id must be freshly generated, not reused", ids.add(id));
    }
  }

  @Test
  public void differentStoresProduceDifferentSessions() {
    SessionService a = new SessionService();
    SessionService b = new SessionService();

    assertNotEquals(a.create(), b.create());
  }

  @Test
  public void aSessionExpiresAfterItsLifetime() {
    MutableClock clock = new MutableClock();
    SessionService store = new SessionService(clock);
    String sessionId = store.create();

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
    String kept = store.create();
    String other = store.create();

    store.invalidateAllExcept(kept);

    assertTrue(store.isValid(kept));
    assertFalse(store.isValid(other));
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
