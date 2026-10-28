package net.pieroxy.mom.utils.mail;

import net.pieroxy.mom.config.credentials.Credential;
import net.pieroxy.mom.config.credentials.OAuthGrant;
import net.pieroxy.mom.config.credentials.OAuthProvider;
import net.pieroxy.mom.utils.mail.oauth.MicrosoftIdentity;
import org.junit.Test;

import javax.mail.MessagingException;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class MailLoginsTest {
  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T10:00:00Z"), ZoneOffset.UTC);

  /** Answers each refresh with the next of {@code answers}: a Tokens, or an exception to throw. */
  private static class FakeIdentity extends MicrosoftIdentity {
    final List<Object> answers;
    final List<String> refreshedWith = new ArrayList<>();

    FakeIdentity(Object... answers) {
      super(URI.create("http://unused/"));
      this.answers = new ArrayList<>(List.of(answers));
    }

    @Override
    public Tokens refresh(String clientId, String refreshToken) throws IOException, OAuthException {
      refreshedWith.add(refreshToken);
      Object answer = answers.remove(0);
      if (answer instanceof IOException e) throw e;
      if (answer instanceof OAuthException e) throw e;
      return (Tokens) answer;
    }
  }

  private static Credential oauthLogin(OAuthGrant grant) {
    Credential credential = new Credential();
    credential.setUsername("me@outlook.com");
    credential.setOauth(grant);
    return credential;
  }

  @Test
  public void aPasswordLoginUsesThePasswordAndPlainMechanisms() throws Exception {
    Credential credential = new Credential();
    credential.setPassword("secret");
    Properties props = new Properties();

    MailLogins.configure(props, "imaps", credential);

    assertEquals("secret", MailLogins.secret(credential, new FakeIdentity(), CLOCK));
    assertTrue(props.isEmpty());
  }

  @Test
  public void anOAuthLoginOnlyOffersXoauth2() {
    Properties props = new Properties();

    MailLogins.configure(props, "smtp", oauthLogin(new OAuthGrant(OAuthProvider.MICROSOFT, "client", "rt")));

    assertEquals("XOAUTH2", props.getProperty("mail.smtp.auth.mechanisms"));
    assertEquals("true", props.getProperty("mail.smtp.auth.plain.disable"));
    assertEquals("true", props.getProperty("mail.smtp.auth.login.disable"));
  }

  @Test
  public void reusesAnAccessTokenUntilItIsAboutToExpire() throws Exception {
    OAuthGrant grant = new OAuthGrant(OAuthProvider.MICROSOFT, "client", "rt");
    grant.setAccessToken("current", CLOCK.millis() + MailLogins.EXPIRY_MARGIN_MS + 1000);
    FakeIdentity identity = new FakeIdentity();

    assertEquals("current", MailLogins.secret(oauthLogin(grant), identity, CLOCK));
    assertTrue(identity.refreshedWith.isEmpty());
  }

  @Test
  public void refreshesAnAccessTokenAboutToExpireAndKeepsTheRotatedRefreshToken() throws Exception {
    OAuthGrant grant = new OAuthGrant(OAuthProvider.MICROSOFT, "client", "rt-1");
    grant.setAccessToken("old", CLOCK.millis() + MailLogins.EXPIRY_MARGIN_MS - 1000);
    List<String> rotations = new ArrayList<>();
    grant.setOnRefreshTokenRotated(() -> rotations.add(grant.getRefreshToken()));
    FakeIdentity identity = new FakeIdentity(new MicrosoftIdentity.Tokens("new", "rt-2", 3600, null));

    assertEquals("new", MailLogins.secret(oauthLogin(grant), identity, CLOCK));

    assertEquals(List.of("rt-1"), identity.refreshedWith);
    assertEquals(CLOCK.millis() + 3_600_000, grant.getAccessTokenExpiresAtMs());
    assertEquals(List.of("rt-2"), rotations);
  }

  @Test
  public void keepsTheRefreshTokenWhenNoNewOneComes() throws Exception {
    OAuthGrant grant = new OAuthGrant(OAuthProvider.MICROSOFT, "client", "rt-1");
    grant.setOnRefreshTokenRotated(() -> {
      throw new AssertionError("not rotated");
    });

    MailLogins.secret(oauthLogin(grant), new FakeIdentity(new MicrosoftIdentity.Tokens("new", null, 3600, null)), CLOCK);

    assertEquals("rt-1", grant.getRefreshToken());
  }

  @Test
  public void aRevokedSignInAsksToSignInAgain() {
    OAuthGrant grant = new OAuthGrant(OAuthProvider.MICROSOFT, "client", "rt");
    FakeIdentity identity = new FakeIdentity(new MicrosoftIdentity.OAuthException("invalid_grant", "AADSTS70008: expired."));

    MailLogins.SignInExpiredException e = assertThrows(MailLogins.SignInExpiredException.class,
        () -> MailLogins.secret(oauthLogin(grant), identity, CLOCK));

    assertTrue(e.getMessage().contains("AADSTS70008"));
    assertNull(grant.getAccessToken());
  }

  @Test
  public void aTransientFailureIsNeitherASignInToRedoNorLostAsANetworkFailure() {
    OAuthGrant grant = new OAuthGrant(OAuthProvider.MICROSOFT, "client", "rt");

    MessagingException busy = assertThrows(MessagingException.class, () -> MailLogins.secret(oauthLogin(grant),
        new FakeIdentity(new MicrosoftIdentity.OAuthException("temporarily_unavailable", null)), CLOCK));
    MessagingException unreachable = assertThrows(MessagingException.class, () -> MailLogins.secret(oauthLogin(grant),
        new FakeIdentity(new IOException("no route")), CLOCK));

    assertFalse(busy instanceof MailLogins.SignInExpiredException);
    assertFalse(unreachable instanceof MailLogins.SignInExpiredException);
    assertTrue(ConnectionFailures.isNetworkFailure(unreachable));
  }
}
