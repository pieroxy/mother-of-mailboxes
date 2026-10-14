package net.pieroxy.mom.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RateLimiterTest {

  @Test
  public void refusesTheHitAboveTheLimitPerKeyCaseInsensitively() {
    RateLimiter limiter = new RateLimiter(60_000, 2);
    limiter.applyHit("admin");
    limiter.applyHit(" Admin ");
    try {
      limiter.applyHit("ADMIN");
      fail("third hit within the window must be refused");
    } catch (RateLimiter.RateExceededException expected) {
      assertTrue("retry once the oldest hit expires", expected.getRetryAfterMillis() > 59_000 && expected.getRetryAfterMillis() <= 60_000);
    }
    limiter.applyHit("other");
  }

  @Test
  public void allowsHitsAgainOnceTheWindowHasPassed() throws InterruptedException {
    RateLimiter limiter = new RateLimiter(50, 1);
    limiter.applyHit("admin");
    Thread.sleep(80);
    limiter.applyHit("admin");
  }

  @Test
  public void dropsKeysWithNoHitLeftInTheWindow() throws InterruptedException {
    RateLimiter limiter = new RateLimiter(50, 5);
    limiter.applyHit("a");
    limiter.applyHit("b");
    assertEquals(2, limiter.getNbKeys());
    Thread.sleep(80);
    limiter.applyHit("c");
    assertEquals(1, limiter.getNbKeys());
    assertEquals(1, limiter.getNbTimeStamps());
  }
}
