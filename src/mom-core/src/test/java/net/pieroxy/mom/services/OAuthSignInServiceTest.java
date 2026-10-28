package net.pieroxy.mom.services;

import net.pieroxy.mom.config.credentials.OAuthGrant;
import net.pieroxy.mom.config.credentials.OAuthProvider;
import net.pieroxy.mom.utils.mail.oauth.MicrosoftIdentity;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

public class OAuthSignInServiceTest {
  private static class MutableClock extends Clock {
    Instant now = Instant.parse("2026-10-28T10:00:00Z");

    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return now; }

    void advance(Duration duration) {
      now = now.plus(duration);
    }
  }

  /** Answers each redeem with the next of {@code redeemAnswers}: a Tokens, or an exception to throw. */
  private static class FakeIdentity extends MicrosoftIdentity {
    final List<Object> redeemAnswers = new ArrayList<>();
    int redeems;

    FakeIdentity() {
      super(URI.create("http://unused/"));
    }

    @Override
    public DeviceCode requestDeviceCode(String clientId) throws OAuthException {
      if (clientId.equals("unknown")) throw new OAuthException("unauthorized_client", "AADSTS700016: Application not found.");
      return new DeviceCode("dc", "CODE", "https://microsoft.com/devicelogin", 900, 5);
    }

    @Override
    public Tokens redeemDeviceCode(String clientId, String deviceCode) throws IOException, OAuthException {
      redeems++;
      Object answer = redeemAnswers.remove(0);
      if (answer instanceof IOException e) throw e;
      if (answer instanceof OAuthException e) throw e;
      return (Tokens) answer;
    }
  }

  private final MutableClock clock = new MutableClock();
  private final FakeIdentity identity = new FakeIdentity();
  private final OAuthSignInService service = new OAuthSignInService(identity, clock);

  @Test
  public void pollsMicrosoftAtMostOncePerIntervalUntilSignedIn() throws Exception {
    identity.redeemAnswers.add(new MicrosoftIdentity.OAuthException("authorization_pending", null));
    identity.redeemAnswers.add(new MicrosoftIdentity.Tokens("at", "rt", 3600, "me@outlook.com"));
    String id = service.start("client").signInId();

    assertEquals(OAuthSignInService.SignInState.PENDING, service.poll(id).state());
    assertEquals(0, identity.redeems);
    clock.advance(Duration.ofSeconds(5));
    assertEquals(OAuthSignInService.SignInState.PENDING, service.poll(id).state());
    assertEquals(OAuthSignInService.SignInState.PENDING, service.poll(id).state());
    assertEquals(1, identity.redeems);
    clock.advance(Duration.ofSeconds(5));
    OAuthSignInService.Status status = service.poll(id);

    assertEquals(new OAuthSignInService.Status(OAuthSignInService.SignInState.SIGNED_IN, "me@outlook.com", null), status);
    OAuthGrant grant = service.grant(id);
    assertEquals(OAuthProvider.MICROSOFT, grant.getProvider());
    assertEquals("client", grant.getClientId());
    assertEquals("rt", grant.getRefreshToken());
    assertEquals("at", grant.getAccessToken());
  }

  @Test
  public void aDeclinedSignInFails() throws Exception {
    identity.redeemAnswers.add(new MicrosoftIdentity.OAuthException("authorization_declined", null));
    String id = service.start("client").signInId();
    clock.advance(Duration.ofSeconds(5));

    OAuthSignInService.Status status = service.poll(id);

    assertEquals(OAuthSignInService.SignInState.FAILED, status.state());
    assertEquals("The sign-in was declined.", status.message());
    assertThrows(IllegalArgumentException.class, () -> service.grant(id));
  }

  @Test
  public void anUnreachableMicrosoftKeepsTheSignInPending() throws Exception {
    identity.redeemAnswers.add(new IOException("no route"));
    String id = service.start("client").signInId();
    clock.advance(Duration.ofSeconds(5));

    assertEquals(OAuthSignInService.SignInState.PENDING, service.poll(id).state());
  }

  @Test
  public void slowDownWidensTheInterval() throws Exception {
    identity.redeemAnswers.add(new MicrosoftIdentity.OAuthException("slow_down", null));
    identity.redeemAnswers.add(new MicrosoftIdentity.OAuthException("authorization_pending", null));
    String id = service.start("client").signInId();
    clock.advance(Duration.ofSeconds(5));
    service.poll(id);

    clock.advance(Duration.ofSeconds(9));
    service.poll(id);
    assertEquals(1, identity.redeems);
    clock.advance(Duration.ofSeconds(1));
    service.poll(id);
    assertEquals(2, identity.redeems);
  }

  @Test
  public void aSignInIsForgottenOnceItsCodeExpiresOrAnHourAfterCompletion() throws Exception {
    String expiring = service.start("client").signInId();
    identity.redeemAnswers.add(new MicrosoftIdentity.Tokens("at", "rt", 3600, null));
    String completed = service.start("client").signInId();
    clock.advance(Duration.ofSeconds(5));
    service.poll(completed);

    clock.advance(Duration.ofSeconds(900));
    assertThrows(IllegalArgumentException.class, () -> service.poll(expiring));
    service.grant(completed);
    clock.advance(OAuthSignInService.GRANT_LIFETIME);
    assertThrows(IllegalArgumentException.class, () -> service.grant(completed));
  }

  @Test
  public void anUnknownClientIdIsRefusedWithMicrosoftsReason() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.start("unknown"));

    assertEquals("Microsoft refused the sign-in: AADSTS700016: Application not found.", e.getMessage());
  }

  @Test
  public void eachSignInHasItsOwnId() throws Exception {
    assertNotEquals(service.start("client").signInId(), service.start("client").signInId());
  }

  @Test
  public void aForgottenSignInIsGone() throws Exception {
    identity.redeemAnswers.add(new MicrosoftIdentity.Tokens("at", "rt", 3600, null));
    String id = service.start("client").signInId();
    clock.advance(Duration.ofSeconds(5));
    service.poll(id);

    service.forget(id);

    assertThrows(IllegalArgumentException.class, () -> service.grant(id));
  }
}
