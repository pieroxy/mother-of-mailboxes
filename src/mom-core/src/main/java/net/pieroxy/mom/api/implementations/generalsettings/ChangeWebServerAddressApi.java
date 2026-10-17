package net.pieroxy.mom.api.implementations.generalsettings;

import net.pieroxy.mom.api.metadata.AbstractAuthenticatedEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.AuthenticatedApiInput;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.services.IServiceProvider;
import net.pieroxy.mom.services.WebServerService;

/** Moves the web server to another address — see {@link WebServerService#changeAddress}. */
@Endpoint(method = ApiMethod.POST)
public class ChangeWebServerAddressApi extends AbstractAuthenticatedEndpoint<ChangeWebServerAddressApiInput, ChangeWebServerAddressApiOutput> {
  public ChangeWebServerAddressApi(IServiceProvider serviceProvider) {
    super(serviceProvider);
  }

  @Override
  public ChangeWebServerAddressApiOutput processAuthenticated(ChangeWebServerAddressApiInput input) {
    return new ChangeWebServerAddressApiOutput(serviceProvider.getWebServerService().changeAddress(input.getAddress()));
  }
}

@TypeScriptType
class ChangeWebServerAddressApiInput extends AuthenticatedApiInput {
  /** Blank = all interfaces. */
  private String address;

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }
}

@TypeScriptType
class ChangeWebServerAddressApiOutput {
  /** False when the switch only happens a moment after this response (see WebServerService#changeAddress). */
  private boolean appliedImmediately;

  public ChangeWebServerAddressApiOutput() {
  }

  public ChangeWebServerAddressApiOutput(boolean appliedImmediately) {
    this.appliedImmediately = appliedImmediately;
  }

  public boolean isAppliedImmediately() {
    return appliedImmediately;
  }

  public void setAppliedImmediately(boolean appliedImmediately) {
    this.appliedImmediately = appliedImmediately;
  }
}
