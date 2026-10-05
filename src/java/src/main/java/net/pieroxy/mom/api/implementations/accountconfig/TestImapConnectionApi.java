package net.pieroxy.mom.api.implementations.accountconfig;

import net.pieroxy.mom.services.IServiceProvider;
import net.pieroxy.mom.api.metadata.AbstractAuthenticatedEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.AuthenticatedApiInput;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.config.credentials.Credential;
import net.pieroxy.mom.config.general.MailAccountConfiguration;
import net.pieroxy.mom.rules.MailAccount;
import net.pieroxy.mom.utils.mail.ImapMailbox;
import net.pieroxy.mom.utils.mail.ImapMailboxConnection;
import net.pieroxy.mom.utils.mail.ImapSessions;

import javax.mail.AuthenticationFailedException;
import javax.mail.MessagingException;
import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * Attempts a live IMAPS connection with whatever host/port/username/password the caller is
 * currently editing — not necessarily saved yet (the whole point: catching a typo before "Save
 * Changes" restarts the account against it). A blank password falls back to the named account's
 * already-saved one, same "blank means unchanged" rule {@code UpdateAccountApi} uses, so testing
 * right after opening Edit — without retyping a password you're not changing — tests the real
 * thing. Never throws on a failed connection: that's the expected, common result of testing, not
 * a server error, so the outcome travels in the output ({@link TestImapConnectionApiOutput}) for
 * the caller to show inline, rather than as a generic API-failure notification.
 */
@Endpoint(method = ApiMethod.POST)
public class TestImapConnectionApi extends AbstractAuthenticatedEndpoint<TestImapConnectionApiInput, TestImapConnectionApiOutput> {
  public TestImapConnectionApi(IServiceProvider serviceProvider) {
    super(serviceProvider);
  }

  @Override
  public TestImapConnectionApiOutput processAuthenticated(TestImapConnectionApiInput input) {
    String password = resolvePassword(input);

    MailAccountConfiguration config = new MailAccountConfiguration();
    config.setHost(input.getHost());
    config.setPort(input.getPort());
    config.setConnectTimeout(input.getConnectTimeout());
    config.setReadTimeout(input.getReadTimeout());
    Credential credential = new Credential();
    credential.setUsername(input.getUsername());
    credential.setPassword(password);

    try (ImapMailbox ignored = ImapMailboxConnection.connect(config, credential)) {
      return new TestImapConnectionApiOutput(true, "Connected successfully.");
    } catch (AuthenticationFailedException e) {
      return new TestImapConnectionApiOutput(false, "Authentication failed — check the username and password.");
    } catch (MessagingException e) {
      return new TestImapConnectionApiOutput(false, describe(e, config));
    }
  }

  private String resolvePassword(TestImapConnectionApiInput input) {
    if (input.getPassword() != null && !input.getPassword().isBlank()) return input.getPassword();
    if (input.getAccountName() == null || input.getAccountName().isBlank()) return input.getPassword();
    return serviceProvider.getAccountService().getAccounts().stream()
        .filter(a -> a.getAccountLabel().equals(input.getAccountName()))
        .findFirst()
        .map(a -> a.getCredential().getPassword())
        .orElse(input.getPassword());
  }

  /** Every failure but a bad login surfaces as the same MessagingException type — disambiguated here via its cause chain. */
  private static String describe(MessagingException e, MailAccountConfiguration config) {
    for (Throwable cause = e; cause != null; cause = cause.getCause()) {
      if (cause instanceof UnknownHostException) return "Could not resolve host \"" + cause.getMessage() + "\".";
      if (cause instanceof SocketTimeoutException) return describeTimeout((SocketTimeoutException) cause, config);
      if (cause instanceof ConnectException) return "Connection refused — check the host and port.";
      if (cause instanceof SSLException) return "TLS handshake failed: " + cause.getMessage();
    }
    return e.getMessage() != null ? e.getMessage() : "Connection failed.";
  }

  /** The JDK only tells a connect timeout from a read timeout through its message ("Connect timed out" vs "Read timed out"). */
  private static String describeTimeout(SocketTimeoutException e, MailAccountConfiguration config) {
    if (e.getMessage() != null && e.getMessage().toLowerCase().startsWith("connect")) {
      return "Connection timed out after " + ImapSessions.connectTimeoutSeconds(config) + " seconds.";
    }
    return "Server did not answer within " + ImapSessions.readTimeoutSeconds(config) + " seconds.";
  }
}

@TypeScriptType
class TestImapConnectionApiInput extends AuthenticatedApiInput {
  /** Only used to fall back to the saved password when {@link #password} is blank — see the class doc comment. */
  private String accountName;
  private String host;
  private int port;
  private int connectTimeout;
  private int readTimeout;
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
