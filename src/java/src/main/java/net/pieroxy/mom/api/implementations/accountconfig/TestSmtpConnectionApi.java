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
 * being edited, then disconnects without sending anything. A blank password falls back to the
 * saved one: the account's SMTP login's if it has its own and {@code ownCredentials} is set, its
 * IMAP login's if {@code ownCredentials} isn't set.
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
    Credential credential = new Credential();
    credential.setUsername(input.getUsername());
    credential.setPassword(resolvePassword(input));

    try (Transport ignored = SmtpSessions.connect(account, smtp, credential)) {
      return new TestSmtpConnectionApiOutput(true, "Connected successfully.");
    } catch (AuthenticationFailedException e) {
      return new TestSmtpConnectionApiOutput(false, "Authentication failed — check the username and password.");
    } catch (MessagingException e) {
      return new TestSmtpConnectionApiOutput(false, ConnectionFailures.describe(e, account));
    }
  }

  private String resolvePassword(TestSmtpConnectionApiInput input) {
    if (input.getPassword() != null && !input.getPassword().isBlank()) return input.getPassword();
    if (input.getAccountName() == null || input.getAccountName().isBlank()) return input.getPassword();
    MailAccountConfiguration saved;
    try {
      saved = serviceProvider.getSettingsService().findAccountConfig(input.getAccountName());
    } catch (IllegalArgumentException e) {
      return input.getPassword();
    }
    boolean savedOwnCredentials = saved.getSmtp() != null && saved.getSmtp().getCredentials() != null;
    if (input.isOwnCredentials() && !savedOwnCredentials) return input.getPassword();
    String key = input.isOwnCredentials() ? saved.getSmtp().getCredentials() : saved.getCredentials();
    return serviceProvider.getSettingsService().resolveCredential(key, "mail account \"" + input.getAccountName() + "\"").getPassword();
  }
}

@TypeScriptType
class TestSmtpConnectionApiInput extends AuthenticatedApiInput {
  /** Only used to fall back to a saved password when {@link #password} is blank — see the class doc comment. */
  private String accountName;
  private String host;
  private int port;
  private SmtpSecurity security;
  private int connectTimeout;
  private int readTimeout;
  private boolean ownCredentials;
  private String username;
  private String password;

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
