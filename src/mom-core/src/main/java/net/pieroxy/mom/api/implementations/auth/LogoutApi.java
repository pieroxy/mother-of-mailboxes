package net.pieroxy.mom.api.implementations.auth;

import net.pieroxy.mom.services.IServiceProvider;
import net.pieroxy.mom.api.metadata.AbstractApiEndpoint;
import net.pieroxy.mom.api.metadata.ApiContext;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;

@Endpoint(method = ApiMethod.POST)
public class LogoutApi extends AbstractApiEndpoint<LogoutApiInput, LogoutApiOutput> {
  private final IServiceProvider serviceProvider;

  public LogoutApi(IServiceProvider serviceProvider) {
    this.serviceProvider = serviceProvider;
  }

  @Override
  public LogoutApiOutput process(LogoutApiInput input, ApiContext context) {
    serviceProvider.getSessionService().invalidate(context.getSessionId());
    context.clearSessionCookie(serviceProvider.getSettingsService().isSessionCookieSecure());
    return new LogoutApiOutput(true);
  }
}

@TypeScriptType
class LogoutApiInput {
}

@TypeScriptType
class LogoutApiOutput {
  private boolean ok;

  public LogoutApiOutput() {
  }

  public LogoutApiOutput(boolean ok) {
    this.ok = ok;
  }

  public boolean isOk() {
    return ok;
  }

  public void setOk(boolean ok) {
    this.ok = ok;
  }
}
