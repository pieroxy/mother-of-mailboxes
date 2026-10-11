package net.pieroxy.mom.api.implementations.generalsettings;

import net.pieroxy.mom.api.metadata.AbstractAuthenticatedEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.AuthenticatedApiInput;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.services.IServiceProvider;
import net.pieroxy.mom.services.WebServerService;

/**
 * Turns the session cookie's Secure flag on or off — see {@link WebServerService#changeSecureCookie}.
 * The caller's own cookie is re-issued accordingly by {@link AbstractAuthenticatedEndpoint}.
 */
@Endpoint(method = ApiMethod.POST)
public class ChangeWebServerSecureCookieApi
    extends AbstractAuthenticatedEndpoint<ChangeWebServerSecureCookieApiInput, ChangeWebServerSecureCookieApiOutput> {
  public ChangeWebServerSecureCookieApi(IServiceProvider serviceProvider) {
    super(serviceProvider);
  }

  @Override
  public ChangeWebServerSecureCookieApiOutput processAuthenticated(ChangeWebServerSecureCookieApiInput input) {
    serviceProvider.getWebServerService().changeSecureCookie(input.isSecureCookie());
    return new ChangeWebServerSecureCookieApiOutput();
  }
}

@TypeScriptType
class ChangeWebServerSecureCookieApiInput extends AuthenticatedApiInput {
  private boolean secureCookie;

  public boolean isSecureCookie() {
    return secureCookie;
  }

  public void setSecureCookie(boolean secureCookie) {
    this.secureCookie = secureCookie;
  }
}

@TypeScriptType
class ChangeWebServerSecureCookieApiOutput {
}
