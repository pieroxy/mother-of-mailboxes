package net.pieroxy.mom.services;

import org.junit.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
}
