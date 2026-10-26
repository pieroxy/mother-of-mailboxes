package net.pieroxy.mom.config.general;

import net.pieroxy.mom.utils.CredentialsResolver;

import java.util.List;

/** How a mail account sends mail. Timeouts are the account's own (connectTimeout/readTimeout). */
public class SmtpConfiguration {
  private String host;
  private int port;
  private SmtpSecurity security;
  /** Sender address of the mail MOM sends for this account. */
  private String fromAddress;
  /**
   * The account's other addresses (e.g. aliases delivered to the same mailbox): {@code
   * name@domain}, or {@code @domain} for a whole domain. Absent: none.
   */
  private List<String> aliases;
  /**
   * Key into credentials.json's top-level "credentials" map — see {@link CredentialsResolver}.
   * Absent: the account's IMAP login is used.
   */
  private String credentials;

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

  public String getFromAddress() {
    return fromAddress;
  }

  public void setFromAddress(String fromAddress) {
    this.fromAddress = fromAddress;
  }

  public String getCredentials() {
    return credentials;
  }

  public void setCredentials(String credentials) {
    this.credentials = credentials;
  }

  public List<String> getAliases() {
    return aliases;
  }

  public void setAliases(List<String> aliases) {
    this.aliases = aliases;
  }
}
