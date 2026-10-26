package net.pieroxy.mom.services;

import net.pieroxy.mom.utils.RateLimiter;

import java.time.Duration;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Per-client-address protection of the web server, keyed on the address {@code AccessValve} settled
 * on (the real client's behind a trusted proxy). Only failed attempts count, so neither a legitimate
 * user nor someone else's attack can lock anyone out:
 * <ul>
 *   <li>failed logins lock logging in, for {@link #FAILED_LOGIN_WINDOW};</li>
 *   <li>404s without a session, typically from a scanner probing paths, ban the address from the
 *   whole web server for {@link #BAN_DURATION}.</li>
 * </ul>
 */
public class ClientGuardService implements Service {
  public static final int MAX_FAILED_LOGINS = 5;
  public static final Duration FAILED_LOGIN_WINDOW = Duration.ofMinutes(5);
  public static final int MAX_NOT_FOUND = 4;
  public static final Duration NOT_FOUND_WINDOW = Duration.ofSeconds(10);
  public static final Duration BAN_DURATION = Duration.ofMinutes(15);
  /** Requested at the site root by browsers and crawlers on their own: a missing one must not count. */
  public static final Set<String> IGNORED_NOT_FOUND_PATHS = Set.of(
      "/favicon.ico", "/apple-touch-icon.png", "/apple-touch-icon-precomposed.png", "/robots.txt");
  /** The webapp's hashed bundles: a page loaded before a redeploy still asks for the old hash. */
  public static final Pattern IGNORED_NOT_FOUND_PATTERN = Pattern.compile("/bundle-[^/]*\\.(js|css)");

  private final RateLimiter failedLogins = new RateLimiter((int) FAILED_LOGIN_WINDOW.toMillis(), MAX_FAILED_LOGINS);
  private final RateLimiter notFound;
  // One hit per ban: check() then refuses the address until that hit is banDuration old.
  private final RateLimiter bans;

  public ClientGuardService() {
    this(NOT_FOUND_WINDOW, BAN_DURATION);
  }

  /** Visible for tests: shorter durations. */
  ClientGuardService(Duration notFoundWindow, Duration banDuration) {
    notFound = new RateLimiter((int) notFoundWindow.toMillis(), MAX_NOT_FOUND);
    bans = new RateLimiter((int) banDuration.toMillis(), 1);
  }

  /** @throws RateLimiter.RateExceededException when {@code clientAddress} failed {@link #MAX_FAILED_LOGINS} times within {@link #FAILED_LOGIN_WINDOW}. */
  public void checkLogin(String clientAddress) {
    failedLogins.check(clientAddress);
  }

  public void loginFailed(String clientAddress) {
    failedLogins.hit(clientAddress);
  }

  public void loginSucceeded(String clientAddress) {
    failedLogins.reset(clientAddress);
  }

  /** @throws RateLimiter.RateExceededException while {@code clientAddress} is banned, with the time left. */
  public void checkBanned(String clientAddress) {
    bans.check(clientAddress);
  }

  /**
   * Counts a 404 answered to a request without a valid session. Paths in
   * {@link #IGNORED_NOT_FOUND_PATHS} or matching {@link #IGNORED_NOT_FOUND_PATTERN} don't count.
   *
   * @return true if this 404 got the address banned.
   */
  public boolean notFound(String clientAddress, String path) {
    if (IGNORED_NOT_FOUND_PATHS.contains(path) || IGNORED_NOT_FOUND_PATTERN.matcher(path).matches()) return false;
    notFound.hit(clientAddress);
    try {
      notFound.check(clientAddress);
      return false;
    } catch (RateLimiter.RateExceededException e) {
      notFound.reset(clientAddress);
      bans.hit(clientAddress);
      return true;
    }
  }
}
