package net.pieroxy.mom.api.implementations.auth;

import net.pieroxy.mom.services.ClientGuardService;
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

  public LoginApi(IServiceProvider serviceProvider) {
    this.serviceProvider = serviceProvider;
  }

  /**
   * Refused outright, before the password is even checked, once the client's address failed too
   * many times (see {@link ClientGuardService}). A missing login fails like a wrong password.
   */
  @Override
  public LoginApiOutput process(LoginApiInput input, ApiContext context) {
    ClientGuardService guard = serviceProvider.getClientGuardService();
    String client = context.getRemoteAddr();
    try {
      guard.checkLogin(client);
    } catch (RateLimiter.RateExceededException e) {
      long retryAfterSeconds = Math.max(1, (e.getRetryAfterMillis() + 999) / 1000);
      LOGGER.warning("Login refused for " + client + ": too many failed attempts, retry in " + retryAfterSeconds + " s");
      throw new TooManyRequestsException("Too many failed logins, retry in " + retryAfterSeconds + " s.", retryAfterSeconds);
    }

    Credential expected = serviceProvider.getSettingsService().getWebServerCredential();
    boolean ok = input.getLogin() != null
        && expected != null
        && expected.getUsername().equals(input.getLogin())
        && PasswordHasher.verify(input.getPassword(), expected.getPasswordHash());
    if (!ok) {
      guard.loginFailed(client);
      LOGGER.warning("Failed login for \"" + printable(input.getLogin()) + "\" from " + client);
      return new LoginApiOutput(false, false);
    }
    guard.loginSucceeded(client);
    context.setSessionCookie(serviceProvider.getSessionService().create(client, context.getUserAgent()), serviceProvider.getSettingsService().isSessionCookieSecure());
    LoginApiOutput output = new LoginApiOutput(true, expected.isTemporary());
    output.setSetupInProgress(serviceProvider.getSettingsService().isSetupInProgress());
    return output;
  }

  /** The submitted login, safe to log: control characters escaped, so it can't forge a log line. */
  private static String printable(String login) {
    if (login == null) return "";
    StringBuilder sb = new StringBuilder();
    for (char c : login.toCharArray()) {
      if (c < 0x20 || c == 0x7f) sb.append(String.format("\\x%02x", (int) c));
      else sb.append(c);
    }
    return sb.toString();
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
