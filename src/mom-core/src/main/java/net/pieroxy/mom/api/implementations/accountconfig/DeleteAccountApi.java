package net.pieroxy.mom.api.implementations.accountconfig;

import net.pieroxy.mom.services.IServiceProvider;
import net.pieroxy.mom.api.metadata.AbstractAuthenticatedEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.AuthenticatedApiInput;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;

/**
 * Permanently removes one account — see {@link net.pieroxy.mom.services.AccountService#deleteAccount}
 * for exactly what that does and doesn't touch. Irreversible from here: the webapp confirms with
 * the user before ever calling this (see AccountSettingsPage's "Danger Zone" section).
 */
@Endpoint(method = ApiMethod.POST)
public class DeleteAccountApi extends AbstractAuthenticatedEndpoint<DeleteAccountApiInput, DeleteAccountApiOutput> {
  public DeleteAccountApi(IServiceProvider serviceProvider) {
    super(serviceProvider);
  }

  @Override
  public DeleteAccountApiOutput processAuthenticated(DeleteAccountApiInput input) {
    if (input.getAccountName() == null || input.getAccountName().isBlank()) {
      throw new IllegalArgumentException("accountName must not be blank.");
    }
    serviceProvider.getAccountService().deleteAccount(input.getAccountName());
    return new DeleteAccountApiOutput();
  }
}

@TypeScriptType
class DeleteAccountApiInput extends AuthenticatedApiInput {
  private String accountName;

  public String getAccountName() {
    return accountName;
  }

  public void setAccountName(String accountName) {
    this.accountName = accountName;
  }
}

@TypeScriptType
class DeleteAccountApiOutput {
}
