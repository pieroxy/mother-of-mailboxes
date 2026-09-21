package net.pieroxy.imf.api;

import org.junit.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class SessionStoreTest {
  @Test
  public void aCreatedSessionIsValid() {
    SessionStore store = new SessionStore();

    String sessionId = store.create();

    assertTrue(store.isValid(sessionId));
  }

  @Test
  public void aRandomIdIsNotValid() {
    SessionStore store = new SessionStore();

    assertFalse(store.isValid("not-a-real-session-id"));
  }

  @Test
  public void nullIsNeverValid() {
    SessionStore store = new SessionStore();

    assertFalse(store.isValid(null));
  }

  @Test
  public void invalidatingRemovesTheSession() {
    SessionStore store = new SessionStore();
    String sessionId = store.create();

    store.invalidate(sessionId);

    assertFalse(store.isValid(sessionId));
  }

  @Test
  public void invalidatingNullIsANoop() {
    SessionStore store = new SessionStore();
    String sessionId = store.create();

    store.invalidate(null);

    assertTrue("invalidate(null) must not wipe out unrelated sessions", store.isValid(sessionId));
  }

  @Test
  public void invalidatingAnUnknownSessionIsANoop() {
    SessionStore store = new SessionStore();

    store.invalidate("never-created");
    // No exception: that's the whole assertion.
  }

  @Test
  public void eachCreatedSessionIsDistinct() {
    SessionStore store = new SessionStore();
    Set<String> ids = ConcurrentHashMap.newKeySet();

    for (int i = 0; i < 1000; i++) {
      String id = store.create();
      assertTrue("id must be freshly generated, not reused", ids.add(id));
    }
  }

  @Test
  public void differentStoresProduceDifferentSessions() {
    SessionStore a = new SessionStore();
    SessionStore b = new SessionStore();

    assertNotEquals(a.create(), b.create());
  }
}
