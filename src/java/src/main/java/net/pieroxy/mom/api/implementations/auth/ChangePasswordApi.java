package net.pieroxy.mom.api.implementations.auth;

import net.pieroxy.mom.services.IServiceProvider;
import net.pieroxy.mom.api.metadata.AbstractAuthenticatedEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.AuthenticatedApiInput;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;

/** Changes the web login's password. The only endpoint usable while that password is temporary. */
@Endpoint(method = ApiMethod.POST)
public class ChangePasswordApi extends AbstractAuthenticatedEndpoint<ChangePasswordApiInput, ChangePasswordApiOutput> {
  public ChangePasswordApi(IServiceProvider serviceProvider) {
    super(serviceProvider);
  }

  @Override
  protected boolean allowedWithTemporaryPassword() {
    return true;
  }

  @Override
  public ChangePasswordApiOutput processAuthenticated(ChangePasswordApiInput input) {
    serviceProvider.getSettingsService().changeWebServerPassword(input.getNewPassword());
    return new ChangePasswordApiOutput(true);
  }
}

@TypeScriptType
class ChangePasswordApiInput extends AuthenticatedApiInput {
  private String newPassword;

  public String getNewPassword() {
    return newPassword;
  }

  public void setNewPassword(String newPassword) {
    this.newPassword = newPassword;
  }
}

@TypeScriptType
class ChangePasswordApiOutput {
  private boolean ok;

  public ChangePasswordApiOutput() {
  }

  public ChangePasswordApiOutput(boolean ok) {
    this.ok = ok;
  }

  public boolean isOk() {
    return ok;
  }

  public void setOk(boolean ok) {
    this.ok = ok;
  }
}
