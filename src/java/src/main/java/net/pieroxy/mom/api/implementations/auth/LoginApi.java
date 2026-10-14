package net.pieroxy.mom.api.implementations.auth;

import net.pieroxy.mom.services.IServiceProvider;
import net.pieroxy.mom.api.metadata.AbstractApiEndpoint;
import net.pieroxy.mom.api.metadata.ApiContext;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TooManyRequestsException;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.config.credentials.Credential;
import net.pieroxy.mom.config.credentials.PasswordHasher;
import net.pieroxy.mom.utils.RateLimiter;

import java.util.logging.Logger;

@Endpoint(method = ApiMethod.POST)
public class LoginApi extends AbstractApiEndpoint<LoginApiInput, LoginApiOutput> {
  private final static Logger LOGGER = Logger.getLogger(LoginApi.class.getName());
  private final IServiceProvider serviceProvider;
  private final RateLimiter limiter;

  public LoginApi(IServiceProvider serviceProvider) {
    this.serviceProvider = serviceProvider;
    this.limiter = new RateLimiter(10000, 2);
  }

  @Override
  public LoginApiOutput process(LoginApiInput input, ApiContext context) {
    // Can't match any credential: refused like a wrong password.
    if (input.getLogin() == null) return new LoginApiOutput(false, false);
    try {
      limiter.applyHit(input.getLogin());
    } catch (RateLimiter.RateExceededException e) {
      long retryAfterSeconds = Math.max(1, (e.getRetryAfterMillis() + 999) / 1000);
      LOGGER.warning("Login refused for \"" + input.getLogin() + "\": too many attempts, retry in " + retryAfterSeconds + " s");
      throw new TooManyRequestsException("Too many login attempts, retry in " + retryAfterSeconds + " s.", retryAfterSeconds);
    }
    Credential expected = serviceProvider.getSettingsService().getWebServerCredential();
    boolean ok = expected != null
        && expected.getUsername().equals(input.getLogin())
        && PasswordHasher.verify(input.getPassword(), expected.getPasswordHash());
    if (!ok) return new LoginApiOutput(false, false);
    context.setSessionCookie(serviceProvider.getSessionService().create(), serviceProvider.getSettingsService().isSessionCookieSecure());
    LoginApiOutput output = new LoginApiOutput(true, expected.isTemporary());
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
  private boolean passwordChangeRequired;
  /** The web UI's setup wizard isn't finished: every page but it (and the password change) redirects there. */
  private boolean setupInProgress;

  public LoginApiOutput() {
  }

  public LoginApiOutput(boolean ok, boolean passwordChangeRequired) {
    this.ok = ok;
    this.passwordChangeRequired = passwordChangeRequired;
  }

  public boolean isOk() {
    return ok;
  }

  public void setOk(boolean ok) {
    this.ok = ok;
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
