package net.pieroxy.mom.api.implementations.auth;

import net.pieroxy.mom.services.IServiceProvider;
import net.pieroxy.mom.api.metadata.AbstractApiEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.config.credentials.Credential;
import net.pieroxy.mom.config.credentials.PasswordHasher;

@Endpoint(method = ApiMethod.POST)
public class LoginApi extends AbstractApiEndpoint<LoginApiInput, LoginApiOutput> {
  private final IServiceProvider serviceProvider;

  public LoginApi(IServiceProvider serviceProvider) {
    this.serviceProvider = serviceProvider;
  }

  @Override
  public LoginApiOutput process(LoginApiInput input) {
    Credential expected = serviceProvider.getSettingsService().getWebServerCredential();
    boolean ok = expected != null
        && expected.getUsername().equals(input.getLogin())
        && PasswordHasher.verify(input.getPassword(), expected.getPasswordHash());
    if (!ok) return new LoginApiOutput(false, null, false);
    LoginApiOutput output = new LoginApiOutput(true, serviceProvider.getSessionService().create(), expected.isTemporary());
    output.setSetupInProgress(serviceProvider.getSettingsService().isSetupInProgress());
    return output;
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
  private boolean passwordChangeRequired;
  /** The web UI's setup wizard isn't finished: every page but it (and the password change) redirects there. */
  private boolean setupInProgress;

  public LoginApiOutput() {
  }

  public LoginApiOutput(boolean ok, String sessionId, boolean passwordChangeRequired) {
    this.ok = ok;
    this.sessionId = sessionId;
    this.passwordChangeRequired = passwordChangeRequired;
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

  public boolean isPasswordChangeRequired() {
    return passwordChangeRequired;
  }

  public void setPasswordChangeRequired(boolean passwordChangeRequired) {
    this.passwordChangeRequired = passwordChangeRequired;
  }

  public boolean isSetupInProgress() {
    return setupInProgress;
  }

  public void setSetupInProgress(boolean setupInProgress) {
    this.setupInProgress = setupInProgress;
  }
}
