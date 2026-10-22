package net.pieroxy.mom.api.implementations.accounts;

import net.pieroxy.mom.services.IServiceProvider;
import net.pieroxy.mom.api.metadata.AbstractAuthenticatedEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.AuthenticatedApiInput;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;

/**
 * Asks an account to run a cycle now (see {@link net.pieroxy.mom.rules.MailAccount#requestCycle}).
 * Returns right away: the cycle runs on the account's own thread.
 */
@Endpoint(method = ApiMethod.POST)
public class RunAccountCycleApi extends AbstractAuthenticatedEndpoint<RunAccountCycleApiInput, RunAccountCycleApiOutput> {
  public RunAccountCycleApi(IServiceProvider serviceProvider) {
    super(serviceProvider);
  }

  @Override
  public RunAccountCycleApiOutput processAuthenticated(RunAccountCycleApiInput input) {
    serviceProvider.getAccountService().requestCycle(input.getAccountName());
    return new RunAccountCycleApiOutput();
  }
}

@TypeScriptType
class RunAccountCycleApiInput extends AuthenticatedApiInput {
  private String accountName;

  public String getAccountName() {
    return accountName;
  }

  public void setAccountName(String accountName) {
    this.accountName = accountName;
  }
}

@TypeScriptType
class RunAccountCycleApiOutput {
}
