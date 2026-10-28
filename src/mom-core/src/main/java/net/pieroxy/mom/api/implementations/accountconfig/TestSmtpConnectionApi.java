package net.pieroxy.mom.api.implementations.accountconfig;

import net.pieroxy.mom.api.metadata.AbstractAuthenticatedEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.AuthenticatedApiInput;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.config.credentials.Credential;
import net.pieroxy.mom.config.general.MailAccountConfiguration;
import net.pieroxy.mom.config.general.SmtpConfiguration;
import net.pieroxy.mom.config.general.SmtpSecurity;
import net.pieroxy.mom.services.IServiceProvider;
import net.pieroxy.mom.utils.mail.ConnectionFailures;
import net.pieroxy.mom.utils.mail.SmtpSessions;

import javax.mail.AuthenticationFailedException;
import javax.mail.MessagingException;
import javax.mail.Transport;

/**
 * Same as {@link TestImapConnectionApi}, for SMTP: connects and authenticates with the settings
 * being edited, then disconnects without sending anything. Without {@code ownCredentials}, the
 * username, password and sign-in are the IMAP login's, resolved by {@link EditedImapLogin}. With,
 * a blank password falls back to the account's saved SMTP password, if it has its own.
 */
@Endpoint(method = ApiMethod.POST)
public class TestSmtpConnectionApi extends AbstractAuthenticatedEndpoint<TestSmtpConnectionApiInput, TestSmtpConnectionApiOutput> {
  public TestSmtpConnectionApi(IServiceProvider serviceProvider) {
    super(serviceProvider);
  }

  @Override
  public TestSmtpConnectionApiOutput processAuthenticated(TestSmtpConnectionApiInput input) {
    if (input.getSecurity() == null) return new TestSmtpConnectionApiOutput(false, "Choose how the connection is secured.");

    MailAccountConfiguration account = new MailAccountConfiguration();
    account.setConnectTimeout(input.getConnectTimeout());
    account.setReadTimeout(input.getReadTimeout());
    SmtpConfiguration smtp = new SmtpConfiguration();
    smtp.setHost(input.getHost());
    smtp.setPort(input.getPort());
    smtp.setSecurity(input.getSecurity());
    Credential credential;
    if (input.isOwnCredentials()) {
      credential = new Credential();
      credential.setUsername(input.getUsername());
      credential.setPassword(resolveOwnPassword(input));
    } else {
      credential = EditedImapLogin.resolve(serviceProvider, input.getAccountName(), input.getUsername(), input.getPassword(),
          input.getOauthSignInId());
    }

    try (Transport ignored = SmtpSessions.connect(SmtpSessions.session(account, smtp, credential), smtp, credential)) {
      return new TestSmtpConnectionApiOutput(true, "Connected successfully.");
    } catch (AuthenticationFailedException e) {
      return new TestSmtpConnectionApiOutput(false, ConnectionFailures.describeAuthenticationFailure(e, credential, "SMTP"));
    } catch (MessagingException e) {
      return new TestSmtpConnectionApiOutput(false, ConnectionFailures.describe(e, account));
    }
  }

  private String resolveOwnPassword(TestSmtpConnectionApiInput input) {
    if (input.getPassword() != null && !input.getPassword().isBlank()) return input.getPassword();
    if (input.getAccountName() == null || input.getAccountName().isBlank()) return input.getPassword();
    MailAccountConfiguration saved;
    try {
      saved = serviceProvider.getSettingsService().findAccountConfig(input.getAccountName());
    } catch (IllegalArgumentException e) {
      return input.getPassword();
    }
    if (saved.getSmtp() == null || saved.getSmtp().getCredentials() == null) return input.getPassword();
    return serviceProvider.getSettingsService().resolveCredential(saved.getSmtp().getCredentials(),
        "mail account \"" + input.getAccountName() + "\"").getPassword();
  }
}

@TypeScriptType
class TestSmtpConnectionApiInput extends AuthenticatedApiInput {
  /** Only used to fall back to a saved login when {@link #password} is blank — see the class doc comment. */
  private String accountName;
  private String host;
  private int port;
  private SmtpSecurity security;
  private int connectTimeout;
  private int readTimeout;
  private boolean ownCredentials;
  private String username;
  private String password;
  /** Without {@link #ownCredentials}: the IMAP login's completed Microsoft sign-in, used instead of {@link #password}. */
  private String oauthSignInId;

  public String getOauthSignInId() {
    return oauthSignInId;
  }

  public void setOauthSignInId(String oauthSignInId) {
    this.oauthSignInId = oauthSignInId;
  }

  public String getAccountName() {
    return accountName;
  }

  public void setAccountName(String accountName) {
    this.accountName = accountName;
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public SmtpSecurity getSecurity() {
    return security;
  }

  public void setSecurity(SmtpSecurity security) {
    this.security = security;
  }

  public int getConnectTimeout() {
    return connectTimeout;
  }

  public void setConnectTimeout(int connectTimeout) {
    this.connectTimeout = connectTimeout;
  }

  public int getReadTimeout() {
    return readTimeout;
  }

  public void setReadTimeout(int readTimeout) {
    this.readTimeout = readTimeout;
  }

  public boolean isOwnCredentials() {
    return ownCredentials;
  }

  public void setOwnCredentials(boolean ownCredentials) {
    this.ownCredentials = ownCredentials;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }
}

@TypeScriptType
class TestSmtpConnectionApiOutput {
  private boolean connected;
  private String message;

  public TestSmtpConnectionApiOutput() {
  }

  public TestSmtpConnectionApiOutput(boolean connected, String message) {
    this.connected = connected;
    this.message = message;
  }

  public boolean isConnected() {
    return connected;
  }

  public void setConnected(boolean connected) {
    this.connected = connected;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }
}
