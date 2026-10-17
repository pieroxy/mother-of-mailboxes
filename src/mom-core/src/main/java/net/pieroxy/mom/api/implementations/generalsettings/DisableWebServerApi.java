package net.pieroxy.mom.api.implementations.generalsettings;

import net.pieroxy.mom.api.metadata.AbstractAuthenticatedEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.AuthenticatedApiInput;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.services.IServiceProvider;
import net.pieroxy.mom.services.WebServerService;

/** Turns the web server off — see {@link WebServerService#disable}. */
@Endpoint(method = ApiMethod.POST)
public class DisableWebServerApi extends AbstractAuthenticatedEndpoint<DisableWebServerApiInput, DisableWebServerApiOutput> {
  public DisableWebServerApi(IServiceProvider serviceProvider) {
    super(serviceProvider);
  }

  @Override
  public DisableWebServerApiOutput processAuthenticated(DisableWebServerApiInput input) {
    serviceProvider.getWebServerService().disable();
    return new DisableWebServerApiOutput();
  }
}

@TypeScriptType
class DisableWebServerApiInput extends AuthenticatedApiInput {
}

@TypeScriptType
class DisableWebServerApiOutput {
}
