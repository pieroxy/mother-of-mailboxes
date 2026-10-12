package net.pieroxy.mom.api.implementations.accountconfig;

import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.config.general.SmtpSecurity;

/**
 * An account's SMTP settings as the settings page shows and edits them. Never carries a password.
 * Strings are never null, so the webapp can diff a loaded copy against an edited one.
 */
@TypeScriptType
public class SmtpSettingsDto {
  private String host = "";
  private int port;
  private SmtpSecurity security;
  private String fromAddress = "";
  /** False: the account's IMAP login is used. */
  private boolean ownCredentials;
  /** The SMTP login's username; empty unless {@link #ownCredentials}. */
  private String username = "";

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
}
