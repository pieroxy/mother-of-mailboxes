package net.pieroxy.mom.services;

import net.pieroxy.mom.utils.RateLimiter;
import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ClientGuardServiceTest {
  private static final String SCANNER = "203.0.113.7";
  private static final String VISITOR = "198.51.100.4";

  @Test
  public void bansAfterMaxNotFoundWithinTheWindow() {
    ClientGuardService guard = new ClientGuardService();
    for (int i = 1; i < ClientGuardService.MAX_NOT_FOUND; i++) {
      assertFalse(guard.notFound(SCANNER, "/wp-login.php"));
    }
    guard.checkBanned(SCANNER);

    assertTrue("the last 404 must trigger the ban", guard.notFound(SCANNER, "/.env"));
    assertThrows(RateLimiter.RateExceededException.class, () -> guard.checkBanned(SCANNER));
  }

  @Test
  public void ignoredPathsNeverCount() {
    ClientGuardService guard = new ClientGuardService();
    for (int i = 0; i < 3 * ClientGuardService.MAX_NOT_FOUND; i++) {
      for (String path : ClientGuardService.IGNORED_NOT_FOUND_PATHS) {
        assertFalse(guard.notFound(VISITOR, path));
      }
    }
    guard.checkBanned(VISITOR);
  }

  @Test
  public void aBanOnlyConcernsItsOwnAddress() {
    ClientGuardService guard = new ClientGuardService();
    for (int i = 0; i < ClientGuardService.MAX_NOT_FOUND; i++) guard.notFound(SCANNER, "/admin");

    assertThrows(RateLimiter.RateExceededException.class, () -> guard.checkBanned(SCANNER));
    guard.checkBanned(VISITOR);
  }

  @Test
  public void notFoundsSpreadOverMoreThanTheWindowDontBan() throws InterruptedException {
    ClientGuardService guard = new ClientGuardService(Duration.ofMillis(200), Duration.ofMinutes(15));
    for (int i = 1; i < ClientGuardService.MAX_NOT_FOUND; i++) guard.notFound(VISITOR, "/typo");
    Thread.sleep(300);

    assertFalse(guard.notFound(VISITOR, "/typo"));
    guard.checkBanned(VISITOR);
  }

  @Test
  public void theBanExpires() throws InterruptedException {
    ClientGuardService guard = new ClientGuardService(Duration.ofSeconds(10), Duration.ofMillis(200));
    for (int i = 0; i < ClientGuardService.MAX_NOT_FOUND; i++) guard.notFound(SCANNER, "/admin");
    assertThrows(RateLimiter.RateExceededException.class, () -> guard.checkBanned(SCANNER));

    Thread.sleep(300);
    guard.checkBanned(SCANNER);
    assertFalse("the count starts over after a ban", guard.notFound(SCANNER, "/admin"));
  }
}
