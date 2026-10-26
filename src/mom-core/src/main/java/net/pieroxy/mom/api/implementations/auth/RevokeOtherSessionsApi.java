package net.pieroxy.mom.api.implementations.auth;

import net.pieroxy.mom.api.metadata.AbstractAuthenticatedEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.AuthenticatedApiInput;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.services.IServiceProvider;

/** Logs out every session but the caller's. */
@Endpoint(method = ApiMethod.POST)
public class RevokeOtherSessionsApi extends AbstractAuthenticatedEndpoint<RevokeOtherSessionsApiInput, RevokeOtherSessionsApiOutput> {
  public RevokeOtherSessionsApi(IServiceProvider serviceProvider) {
    super(serviceProvider);
  }

  @Override
  public RevokeOtherSessionsApiOutput processAuthenticated(RevokeOtherSessionsApiInput input) {
    serviceProvider.getSessionService().invalidateAllExcept(input.getSessionId());
    return new RevokeOtherSessionsApiOutput(true);
  }
}

@TypeScriptType
class RevokeOtherSessionsApiInput extends AuthenticatedApiInput {
}

@TypeScriptType
class RevokeOtherSessionsApiOutput {
  private boolean ok;

  public RevokeOtherSessionsApiOutput() {
  }

  public RevokeOtherSessionsApiOutput(boolean ok) {
    this.ok = ok;
  }

  public boolean isOk() {
    return ok;
  }

  public void setOk(boolean ok) {
    this.ok = ok;
  }
}
