package net.pieroxy.mom.api.implementations.auth;

import net.pieroxy.mom.api.metadata.AbstractAuthenticatedEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.AuthenticatedApiInput;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.services.IServiceProvider;

/** Logs out one session, designated by its public ID (see ActiveSessionsApi). */
@Endpoint(method = ApiMethod.POST)
public class RevokeSessionApi extends AbstractAuthenticatedEndpoint<RevokeSessionApiInput, RevokeSessionApiOutput> {
  public RevokeSessionApi(IServiceProvider serviceProvider) {
    super(serviceProvider);
  }

  @Override
  public RevokeSessionApiOutput processAuthenticated(RevokeSessionApiInput input) {
    if (!serviceProvider.getSessionService().invalidateByPublicId(input.getPublicId())) {
      throw new IllegalArgumentException("No such session, it may have expired already.");
    }
    return new RevokeSessionApiOutput(true);
  }
}

@TypeScriptType
class RevokeSessionApiInput extends AuthenticatedApiInput {
  private String publicId;

  public String getPublicId() {
    return publicId;
  }

  public void setPublicId(String publicId) {
    this.publicId = publicId;
  }
}

@TypeScriptType
class RevokeSessionApiOutput {
  private boolean ok;

  public RevokeSessionApiOutput() {
  }

  public RevokeSessionApiOutput(boolean ok) {
    this.ok = ok;
  }

  public boolean isOk() {
    return ok;
  }

  public void setOk(boolean ok) {
    this.ok = ok;
  }
}
