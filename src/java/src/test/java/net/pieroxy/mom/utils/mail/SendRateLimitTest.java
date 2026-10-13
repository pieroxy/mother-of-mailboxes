package net.pieroxy.mom.utils.mail;

import org.junit.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SendRateLimitTest {
  private final MutableClock clock = new MutableClock();

  @Test
  public void refusesOnceTheHourlyMaxIsReachedThenAllowsAgainAnHourLater() {
    SendRateLimit limit = new SendRateLimit(2, clock);

    assertTrue(limit.tryAcquire());
    clock.advance(Duration.ofMinutes(30));
    assertTrue(limit.tryAcquire());
    assertFalse(limit.tryAcquire());

    clock.advance(Duration.ofMinutes(30));
    assertTrue("the first send is now an hour old", limit.tryAcquire());
    assertFalse(limit.tryAcquire());
  }

  @Test
  public void aReleasedSendDoesNotCount() {
    SendRateLimit limit = new SendRateLimit(1, clock);

    assertTrue(limit.tryAcquire());
    limit.release();

    assertTrue(limit.tryAcquire());
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
