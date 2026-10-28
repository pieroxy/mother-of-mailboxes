package net.pieroxy.mom.api.implementations.accountconfig;

import net.pieroxy.mom.services.IServiceProvider;
import net.pieroxy.mom.api.metadata.AbstractAuthenticatedEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.AuthenticatedApiInput;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.config.credentials.Credential;
import net.pieroxy.mom.config.general.MailAccountConfiguration;
import net.pieroxy.mom.utils.mail.ConnectionFailures;
import net.pieroxy.mom.utils.mail.ImapMailbox;
import net.pieroxy.mom.utils.mail.ImapMailboxConnection;

import javax.mail.AuthenticationFailedException;
import javax.mail.MessagingException;

/**
 * Attempts a live IMAPS connection with whatever host/port/login the caller is currently editing —
 * not necessarily saved yet (the whole point: catching a typo before "Save Changes" restarts the
 * account against it). The login is resolved by {@link EditedImapLogin}: a blank password falls
 * back to the named account's saved login, same "blank means unchanged" rule
 * {@code UpdateAccountApi} uses. Never throws on a failed connection: that's the expected, common
 * result of testing, not a server error, so the outcome travels in the output
 * ({@link TestImapConnectionApiOutput}) for the caller to show inline.
 */
@Endpoint(method = ApiMethod.POST)
public class TestImapConnectionApi extends AbstractAuthenticatedEndpoint<TestImapConnectionApiInput, TestImapConnectionApiOutput> {
  public TestImapConnectionApi(IServiceProvider serviceProvider) {
    super(serviceProvider);
  }

  @Override
  public TestImapConnectionApiOutput processAuthenticated(TestImapConnectionApiInput input) {
    MailAccountConfiguration config = new MailAccountConfiguration();
    config.setHost(input.getHost());
    config.setPort(input.getPort());
    config.setConnectTimeout(input.getConnectTimeout());
    config.setReadTimeout(input.getReadTimeout());
    Credential credential = EditedImapLogin.resolve(serviceProvider, input.getAccountName(), input.getUsername(), input.getPassword(),
        input.getOauthSignInId());

    try (ImapMailbox ignored = ImapMailboxConnection.connect(config, credential)) {
      return new TestImapConnectionApiOutput(true, "Connected successfully.");
    } catch (AuthenticationFailedException e) {
      return new TestImapConnectionApiOutput(false, ConnectionFailures.describeAuthenticationFailure(e, credential, "IMAP"));
    } catch (MessagingException e) {
      return new TestImapConnectionApiOutput(false, ConnectionFailures.describe(e, config));
    }
  }
}

@TypeScriptType
class TestImapConnectionApiInput extends AuthenticatedApiInput {
  /** Only used to fall back to the saved login when {@link #password} is blank — see the class doc comment. */
  private String accountName;
  private String host;
  private int port;
  private int connectTimeout;
  private int readTimeout;
  private String username;
  private String password;
  /** A completed Microsoft sign-in (see StartMicrosoftSignInApi), used instead of {@link #password}. */
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
class TestImapConnectionApiOutput {
  private boolean connected;
  private String message;

  public TestImapConnectionApiOutput() {
  }

  public TestImapConnectionApiOutput(boolean connected, String message) {
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
