package net.pieroxy.mom.services;

import net.pieroxy.mom.config.credentials.OAuthGrant;
import net.pieroxy.mom.config.credentials.OAuthProvider;
import net.pieroxy.mom.utils.mail.oauth.MicrosoftIdentity;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Microsoft sign-ins in progress (device code flow), and their grants until an account takes them:
 * the refresh token stays on the server, the browser only holds the sign-in's ID. In memory only.
 */
public class OAuthSignInService implements Service {
  private final static Logger LOGGER = Logger.getLogger(OAuthSignInService.class.getName());
  /** How long a completed sign-in waits for the account to be created or saved. */
  static final Duration GRANT_LIFETIME = Duration.ofHours(1);
  private static final SecureRandom RANDOM = new SecureRandom();

  private final MicrosoftIdentity identity;
  private final Clock clock;
  private final Map<String, SignIn> signIns = new ConcurrentHashMap<>();

  public enum SignInState {
    PENDING, SIGNED_IN, FAILED
  }

  public record Started(String signInId, String userCode, String verificationUri, int expiresInSeconds, int intervalSeconds) {
  }

  /** @param username only when {@link SignInState#SIGNED_IN}, and null if Microsoft didn't tell; {@code message} only when {@link SignInState#FAILED}. */
  public record Status(SignInState state, String username, String message) {
  }

  private static final class SignIn {
    final String clientId;
    final String deviceCode;
    long intervalMs;
    long nextPollAtMs;
    long expiresAtMs;
    SignInState state = SignInState.PENDING;
    OAuthGrant grant;
    String username;
    String message;

    SignIn(String clientId, String deviceCode) {
      this.clientId = clientId;
      this.deviceCode = deviceCode;
    }
  }

  public OAuthSignInService() {
    this(MicrosoftIdentity.DEFAULT, Clock.systemUTC());
  }

  OAuthSignInService(MicrosoftIdentity identity, Clock clock) {
    this.identity = identity;
    this.clock = clock;
  }

  /**
   * @throws IllegalArgumentException when Microsoft refuses the client ID; its message says why.
   * @throws IOException when Microsoft can't be reached.
   */
  public Started start(String clientId) throws IOException {
    purgeExpired();
    MicrosoftIdentity.DeviceCode code;
    try {
      code = identity.requestDeviceCode(clientId);
    } catch (MicrosoftIdentity.OAuthException e) {
      throw new IllegalArgumentException("Microsoft refused the sign-in: " + e.getMessage());
    }
    SignIn signIn = new SignIn(clientId, code.deviceCode());
    long now = clock.millis();
    signIn.intervalMs = code.intervalSeconds() * 1000L;
    signIn.nextPollAtMs = now + signIn.intervalMs;
    signIn.expiresAtMs = now + code.expiresInSeconds() * 1000L;
    String id = HexFormat.of().formatHex(randomBytes());
    signIns.put(id, signIn);
    return new Started(id, code.userCode(), code.verificationUri(), code.expiresInSeconds(), code.intervalSeconds());
  }

  /**
   * Asks Microsoft whether the user is done, unless asked sooner than the interval it set: callers
   * can poll as often as they like.
   *
   * @throws IllegalArgumentException for an unknown or expired sign-in.
   */
  public Status poll(String signInId) {
    SignIn signIn = find(signInId);
    synchronized (signIn) {
      long now = clock.millis();
      if (signIn.state == SignInState.PENDING && now >= signIn.nextPollAtMs) redeem(signIn, now);
      return new Status(signIn.state, signIn.username, signIn.message);
    }
  }

  /** @throws IllegalArgumentException unless the sign-in completed and hasn't expired. */
  public OAuthGrant grant(String signInId) {
    SignIn signIn = find(signInId);
    synchronized (signIn) {
      if (signIn.state != SignInState.SIGNED_IN) throw new IllegalArgumentException("The Microsoft sign-in isn't complete.");
      return signIn.grant;
    }
  }

  /** Once an account holds its grant. */
  public void forget(String signInId) {
    signIns.remove(signInId);
  }

  private void redeem(SignIn signIn, long now) {
    try {
      MicrosoftIdentity.Tokens tokens = identity.redeemDeviceCode(signIn.clientId, signIn.deviceCode);
      signIn.grant = new OAuthGrant(OAuthProvider.MICROSOFT, signIn.clientId, tokens.refreshToken());
      signIn.grant.setAccessToken(tokens.accessToken(), now + tokens.expiresInSeconds() * 1000L);
      signIn.username = tokens.username();
      signIn.state = tokens.refreshToken() != null ? SignInState.SIGNED_IN : SignInState.FAILED;
      signIn.message = tokens.refreshToken() != null ? null : "Microsoft didn't grant lasting access (no refresh token).";
      signIn.expiresAtMs = now + GRANT_LIFETIME.toMillis();
    } catch (MicrosoftIdentity.OAuthException e) {
      switch (e.getError()) {
        case "authorization_pending" -> {
        }
        case "slow_down" -> signIn.intervalMs += 5000;
        case "authorization_declined" -> fail(signIn, "The sign-in was declined.");
        case "expired_token" -> fail(signIn, "The code expired before the sign-in was complete: start again.");
        default -> fail(signIn, e.getMessage());
      }
    } catch (IOException e) {
      // Next poll retries.
      LOGGER.fine(() -> "Could not reach Microsoft to complete a sign-in: " + e.getMessage());
    }
    signIn.nextPollAtMs = now + signIn.intervalMs;
  }

  private static void fail(SignIn signIn, String message) {
    signIn.state = SignInState.FAILED;
    signIn.message = message;
  }

  private SignIn find(String signInId) {
    purgeExpired();
    SignIn signIn = signInId != null ? signIns.get(signInId) : null;
    if (signIn == null) throw new IllegalArgumentException("The Microsoft sign-in expired: sign in again.");
    return signIn;
  }

  private void purgeExpired() {
    long now = clock.millis();
    signIns.values().removeIf(signIn -> now >= signIn.expiresAtMs);
  }

  private static byte[] randomBytes() {
    byte[] bytes = new byte[16];
    RANDOM.nextBytes(bytes);
    return bytes;
  }
}
