package net.pieroxy.mom.api.implementations.accountconfig;

import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.config.general.SmtpConfiguration;
import net.pieroxy.mom.config.general.SmtpSecurity;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

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

  /**
   * Validated and trimmed, without credentials (see {@code SettingsService#setSmtp}).
   *
   * @throws IllegalArgumentException on any invalid field.
   */
  public SmtpConfiguration toConfiguration() {
    if (host == null || host.isBlank()) {
      throw new IllegalArgumentException("SMTP host must not be blank.");
    }
    if (port <= 0 || port > 65535) {
      throw new IllegalArgumentException("SMTP port must be between 1 and 65535.");
    }
    if (security == null) {
      throw new IllegalArgumentException("SMTP security must be set.");
    }
    try {
      new InternetAddress(fromAddress, true).validate();
    } catch (AddressException | NullPointerException e) {
      throw new IllegalArgumentException("SMTP sender address \"" + fromAddress + "\" is not a valid email address.");
    }
    if (ownCredentials && (username == null || username.isBlank())) {
      throw new IllegalArgumentException("SMTP username must not be blank.");
    }
    SmtpConfiguration smtp = new SmtpConfiguration();
    smtp.setHost(host.trim());
    smtp.setPort(port);
    smtp.setSecurity(security);
    smtp.setFromAddress(fromAddress.trim());
    return smtp;
  }
}
