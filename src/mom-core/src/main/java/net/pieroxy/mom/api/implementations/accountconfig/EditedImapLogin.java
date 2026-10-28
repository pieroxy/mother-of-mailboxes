package net.pieroxy.mom.api.implementations.accountconfig;

import net.pieroxy.mom.config.credentials.Credential;
import net.pieroxy.mom.services.IServiceProvider;
import net.pieroxy.mom.rules.MailAccount;

/** The IMAP login being edited in the webapp, not necessarily saved yet. */
public final class EditedImapLogin {
  private EditedImapLogin() {
  }

  /**
   * In order: the completed Microsoft sign-in {@code oauthSignInId}, the {@code password}, then
   * the saved login of {@code accountName} (password or sign-in), with {@code username} in all cases.
   *
   * @param accountName blank for an account not created yet.
   * @throws IllegalArgumentException if the sign-in isn't complete or expired.
   */
  public static Credential resolve(IServiceProvider serviceProvider, String accountName, String username, String password,
                                   String oauthSignInId) {
    Credential credential = new Credential();
    credential.setUsername(username);
    if (oauthSignInId != null && !oauthSignInId.isBlank()) {
      credential.setOauth(serviceProvider.getOAuthSignInService().grant(oauthSignInId));
    } else if ((password == null || password.isBlank()) && accountName != null && !accountName.isBlank()) {
      serviceProvider.getAccountService().getAccounts().stream()
          .filter(a -> a.getAccountLabel().equals(accountName))
          .findFirst()
          .map(MailAccount::getCredential)
          .ifPresent(saved -> {
            credential.setPassword(saved.getPassword());
            credential.setOauth(saved.getOauth());
          });
    } else {
      credential.setPassword(password);
    }
    return credential;
  }
}
