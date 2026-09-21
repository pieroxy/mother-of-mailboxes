package net.pieroxy.imf.api.implementations.auth;

import net.pieroxy.imf.api.ServiceProvider;
import net.pieroxy.imf.api.metadata.AbstractApiEndpoint;
import net.pieroxy.imf.api.metadata.ApiMethod;
import net.pieroxy.imf.api.metadata.Endpoint;
import net.pieroxy.imf.api.metadata.TypeScriptType;
import net.pieroxy.imf.config.credentials.Credential;

@Endpoint(method = ApiMethod.POST)
public class LoginApi extends AbstractApiEndpoint<LoginApiInput, LoginApiOutput> {
  private final ServiceProvider serviceProvider;

  public LoginApi(ServiceProvider serviceProvider) {
    this.serviceProvider = serviceProvider;
  }

  @Override
  public LoginApiOutput process(LoginApiInput input) {
    Credential expected = serviceProvider.getWebServerCredential();
    boolean ok = expected != null
        && expected.getUsername().equals(input.getLogin())
        && expected.getPassword().equals(input.getPassword());
    if (!ok) return new LoginApiOutput(false, null);
    return new LoginApiOutput(true, serviceProvider.getSessionStore().create());
  }
}

@TypeScriptType
class LoginApiInput {
  private String login;
  private String password;

  public String getLogin() {
    return login;
  }

  public void setLogin(String login) {
    this.login = login;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }
}

@TypeScriptType
class LoginApiOutput {
  private boolean ok;
  private String sessionId;

  public LoginApiOutput() {
  }

  public LoginApiOutput(boolean ok, String sessionId) {
    this.ok = ok;
    this.sessionId = sessionId;
  }

  public boolean isOk() {
    return ok;
  }

  public void setOk(boolean ok) {
    this.ok = ok;
  }

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }
}
