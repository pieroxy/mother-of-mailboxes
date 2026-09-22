package net.pieroxy.mom.api.implementations.auth;

import net.pieroxy.mom.api.ServiceProvider;
import net.pieroxy.mom.api.metadata.AbstractApiEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;

@Endpoint(method = ApiMethod.POST)
public class LogoutApi extends AbstractApiEndpoint<LogoutApiInput, LogoutApiOutput> {
  private final ServiceProvider serviceProvider;

  public LogoutApi(ServiceProvider serviceProvider) {
    this.serviceProvider = serviceProvider;
  }

  @Override
  public LogoutApiOutput process(LogoutApiInput input) {
    serviceProvider.getSessionStore().invalidate(input.getSessionId());
    return new LogoutApiOutput(true);
  }
}

@TypeScriptType
class LogoutApiInput {
  private String sessionId;

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }
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
