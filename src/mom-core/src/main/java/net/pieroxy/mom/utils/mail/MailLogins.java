package net.pieroxy.mom.utils.mail;

import net.pieroxy.mom.config.credentials.Credential;
import net.pieroxy.mom.config.credentials.OAuthGrant;
import net.pieroxy.mom.utils.mail.oauth.MicrosoftIdentity;

import javax.mail.AuthenticationFailedException;
import javax.mail.MessagingException;
import java.io.IOException;
import java.time.Clock;
import java.util.Properties;
import java.util.Set;

/** How {@link ImapSessions} and {@link SmtpSessions} log in: with the password, or with XOAUTH2 for an OAuth login. */
public final class MailLogins {
  /** Refreshes an access token this long before it expires, so it can't expire mid-login. */
  static final long EXPIRY_MARGIN_MS = 5 * 60_000;
  // The others (server_error, temporarily_unavailable...) may well succeed on the next attempt.
  private static final Set<String> SIGN_IN_AGAIN_ERRORS = Set.of(
      "invalid_grant", "interaction_required", "consent_required", "invalid_client", "unauthorized_client");

  private MailLogins() {
  }

  /** Only signing in again can fix it. */
  public static class SignInExpiredException extends AuthenticationFailedException {
    SignInExpiredException(String message) {
      super(message);
    }
  }

  /** @param protocol {@code imaps}, {@code smtp} or {@code smtps}. */
  public static void configure(Properties props, String protocol, Credential credential) {
    if (credential.getOauth() == null) return;
    props.setProperty("mail." + protocol + ".auth.mechanisms", "XOAUTH2");
    props.setProperty("mail." + protocol + ".auth.login.disable", "true");
    props.setProperty("mail." + protocol + ".auth.plain.disable", "true");
  }

  /** The password, or for an OAuth login an access token, refreshed when about to expire. */
  public static String secret(Credential credential) throws MessagingException {
    return secret(credential, MicrosoftIdentity.DEFAULT, Clock.systemUTC());
  }

  static String secret(Credential credential, MicrosoftIdentity identity, Clock clock) throws MessagingException {
    OAuthGrant grant = credential.getOauth();
    if (grant == null) return credential.getPassword();
    // Locks out the account's other connections (IDLE, cycle, SMTP) from refreshing it at the same time.
    synchronized (grant) {
      long now = clock.millis();
      if (grant.getAccessToken() != null && now < grant.getAccessTokenExpiresAtMs() - EXPIRY_MARGIN_MS) {
        return grant.getAccessToken();
      }
      try {
        MicrosoftIdentity.Tokens tokens = identity.refresh(grant.getClientId(), grant.getRefreshToken());
        grant.setAccessToken(tokens.accessToken(), now + tokens.expiresInSeconds() * 1000L);
        if (tokens.refreshToken() != null) grant.setRefreshToken(tokens.refreshToken());
        return tokens.accessToken();
      } catch (MicrosoftIdentity.OAuthException e) {
        if (!SIGN_IN_AGAIN_ERRORS.contains(e.getError())) {
          throw new MessagingException("Microsoft refused to refresh the sign-in: " + e.getMessage());
        }
        throw new SignInExpiredException("The Microsoft sign-in is no longer valid (" + e.getMessage()
            + "): sign in again from the account's IMAP settings.");
      } catch (IOException e) {
        throw new MessagingException("Could not refresh the Microsoft sign-in: " + e.getMessage(), e);
      }
    }
  }
}
