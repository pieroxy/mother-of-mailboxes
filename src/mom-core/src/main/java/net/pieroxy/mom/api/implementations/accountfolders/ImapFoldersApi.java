package net.pieroxy.mom.api.implementations.accountfolders;

import net.pieroxy.mom.api.metadata.AbstractAuthenticatedEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.AuthenticatedApiInput;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.config.credentials.Credential;
import net.pieroxy.mom.config.general.MailAccountConfiguration;
import net.pieroxy.mom.services.IServiceProvider;
import net.pieroxy.mom.utils.mail.ImapMailboxConnection;

import javax.mail.MessagingException;

/**
 * Same as {@link AccountFoldersApi}, for an account that doesn't exist yet (the account creation
 * wizard): connects with the given settings instead of a saved account's.
 */
@Endpoint(method = ApiMethod.POST)
public class ImapFoldersApi extends AbstractAuthenticatedEndpoint<ImapFoldersApiInput, AccountFoldersApiOutput> {
  public ImapFoldersApi(IServiceProvider serviceProvider) {
    super(serviceProvider);
  }

  @Override
  public AccountFoldersApiOutput processAuthenticated(ImapFoldersApiInput input) throws MessagingException {
    MailAccountConfiguration config = new MailAccountConfiguration();
    config.setHost(input.getHost());
    config.setPort(input.getPort());
    config.setConnectTimeout(input.getConnectTimeout());
    config.setReadTimeout(input.getReadTimeout());
    Credential credential = new Credential();
    credential.setUsername(input.getUsername());
    credential.setPassword(input.getPassword());

    try (ImapMailboxConnection mailbox = ImapMailboxConnection.connect(config, credential)) {
      return new AccountFoldersApiOutput(AccountFoldersApi.listFolders(mailbox));
    }
  }
}

@TypeScriptType
class ImapFoldersApiInput extends AuthenticatedApiInput {
  private String host;
  private int port;
  private int connectTimeout;
  private int readTimeout;
  private String username;
  private String password;

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
