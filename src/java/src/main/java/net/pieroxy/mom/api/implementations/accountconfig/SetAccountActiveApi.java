package net.pieroxy.mom.api.implementations.accountconfig;

import net.pieroxy.mom.services.IServiceProvider;
import net.pieroxy.mom.api.metadata.AbstractAuthenticatedEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.AuthenticatedApiInput;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;

/**
 * Pauses or resumes an account — see {@link net.pieroxy.mom.services.AccountService#setAccountActive}
 * for exactly what that does. Unlike {@code DeleteAccountApi}, this is fully reversible, so the
 * webapp calls it directly with no confirmation.
 */
@Endpoint(method = ApiMethod.POST)
public class SetAccountActiveApi extends AbstractAuthenticatedEndpoint<SetAccountActiveApiInput, SetAccountActiveApiOutput> {
  public SetAccountActiveApi(IServiceProvider serviceProvider) {
    super(serviceProvider);
  }

  @Override
  public SetAccountActiveApiOutput processAuthenticated(SetAccountActiveApiInput input) {
    if (input.getAccountName() == null || input.getAccountName().isBlank()) {
      throw new IllegalArgumentException("accountName must not be blank.");
    }
    serviceProvider.getAccountService().setAccountActive(input.getAccountName(), input.isActive());
    return new SetAccountActiveApiOutput();
  }
}

@TypeScriptType
class SetAccountActiveApiInput extends AuthenticatedApiInput {
  private String accountName;
  private boolean active;

  public String getAccountName() {
    return accountName;
  }

  public void setAccountName(String accountName) {
    this.accountName = accountName;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }
}

@TypeScriptType
class SetAccountActiveApiOutput {
}
