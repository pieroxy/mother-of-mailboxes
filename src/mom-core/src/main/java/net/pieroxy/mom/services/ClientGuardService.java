package net.pieroxy.mom.services;

import net.pieroxy.mom.utils.RateLimiter;

import java.time.Duration;

/**
 * Per-client-address protection of the web server, keyed on the address {@code AccessValve} settled
 * on (the real client's behind a trusted proxy). Only failed attempts count, so neither a legitimate
 * user nor someone else's attack can lock anyone out.
 */
public class ClientGuardService implements Service {
  public static final int MAX_FAILED_LOGINS = 5;
  public static final Duration FAILED_LOGIN_WINDOW = Duration.ofMinutes(5);

  private final RateLimiter failedLogins = new RateLimiter((int) FAILED_LOGIN_WINDOW.toMillis(), MAX_FAILED_LOGINS);

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
}
