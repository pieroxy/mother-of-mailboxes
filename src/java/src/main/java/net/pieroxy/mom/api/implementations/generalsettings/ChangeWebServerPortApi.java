package net.pieroxy.mom.api.implementations.generalsettings;

import net.pieroxy.mom.api.metadata.AbstractAuthenticatedEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.AuthenticatedApiInput;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.services.IServiceProvider;
import net.pieroxy.mom.services.WebServerService;

/** Moves the web server to another port immediately — see {@link WebServerService#changePort}. */
@Endpoint(method = ApiMethod.POST)
public class ChangeWebServerPortApi extends AbstractAuthenticatedEndpoint<ChangeWebServerPortApiInput, ChangeWebServerPortApiOutput> {
  public ChangeWebServerPortApi(IServiceProvider serviceProvider) {
    super(serviceProvider);
  }

  @Override
  public ChangeWebServerPortApiOutput processAuthenticated(ChangeWebServerPortApiInput input) {
    serviceProvider.getWebServerService().changePort(input.getPort());
    return new ChangeWebServerPortApiOutput();
  }
}

@TypeScriptType
class ChangeWebServerPortApiInput extends AuthenticatedApiInput {
  private int port;

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }
}

@TypeScriptType
class ChangeWebServerPortApiOutput {
}
