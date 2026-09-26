package net.pieroxy.mom.api.implementations.accountconfig;

import net.pieroxy.mom.api.ServiceProvider;
import net.pieroxy.mom.api.metadata.AbstractApiEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;

/**
 * Saves the account's IMAP username/password and restarts it — see
 * {@link ServiceProvider#updateCredentials}. The browser is never shown the real password (see
 * {@code AccountConfigApi}'s {@code CredentialsInfoDto}), so a blank {@code password} here means
 * "leave it as is", never "set it to blank" — the same convention the edit page's empty,
 * unprefilled password field relies on.
 */
@Endpoint(method = ApiMethod.POST)
public class UpdateAccountCredentialsApi extends AbstractApiEndpoint<UpdateAccountCredentialsApiInput, UpdateAccountCredentialsApiOutput> {
  private final ServiceProvider serviceProvider;

  public UpdateAccountCredentialsApi(ServiceProvider serviceProvider) {
    this.serviceProvider = serviceProvider;
  }

  @Override
  public UpdateAccountCredentialsApiOutput process(UpdateAccountCredentialsApiInput input) {
    if (input.getUsername() == null || input.getUsername().isBlank()) {
      throw new IllegalArgumentException("Username must not be blank.");
    }

    serviceProvider.updateCredentials(input.getAccountName(), input.getUsername(), input.getPassword());

    return new UpdateAccountCredentialsApiOutput();
  }
}

@TypeScriptType
class UpdateAccountCredentialsApiInput {
  private String accountName;
  private String username;
  /** Blank means "leave the current password unchanged" — see the class javadoc. */
  private String password;

  public String getAccountName() {
    return accountName;
  }

  public void setAccountName(String accountName) {
    this.accountName = accountName;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }
}

@TypeScriptType
class UpdateAccountCredentialsApiOutput {
}
