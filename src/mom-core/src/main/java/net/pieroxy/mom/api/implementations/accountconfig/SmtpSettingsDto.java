package net.pieroxy.mom.api.implementations.accountconfig;

import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.config.general.SmtpConfiguration;
import net.pieroxy.mom.config.general.SmtpSecurity;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * An account's SMTP settings as the settings page shows and edits them. Never carries a password.
 * Strings and lists are never null, so the webapp can diff a loaded copy against an edited one.
 */
@TypeScriptType
public class SmtpSettingsDto {
  private String host = "";
  private int port;
  private SmtpSecurity security;
  private String fromAddress = "";
  /** See {@link SmtpConfiguration#getAliases}. */
  private List<String> aliases = new ArrayList<>();
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

  public List<String> getAliases() {
    return aliases;
  }

  public void setAliases(List<String> aliases) {
    this.aliases = aliases;
  }

  /**
   * Validated and trimmed, without credentials (see {@code SettingsService#setSmtp}). Aliases are
   * also lower-cased and deduplicated; none gives a null list, so config.json stays without one.
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
    List<String> normalizedAliases = normalizeAliases(aliases);
    smtp.setAliases(normalizedAliases.isEmpty() ? null : normalizedAliases);
    return smtp;
  }

  private static List<String> normalizeAliases(List<String> aliases) {
    Set<String> normalized = new LinkedHashSet<>();
    for (String alias : aliases != null ? aliases : List.<String>of()) {
      String trimmed = alias != null ? alias.trim().toLowerCase(Locale.ROOT) : "";
      if (trimmed.isEmpty()) continue;
      if (trimmed.startsWith("@")) {
        if (!trimmed.matches("@[^@\\s]+\\.[^@\\s]+")) {
          throw new IllegalArgumentException("Alias \"" + alias + "\" is not a valid domain (expected @example.com).");
        }
      } else {
        try {
          if (!trimmed.contains("@")) throw new AddressException();
          new InternetAddress(trimmed, true).validate();
        } catch (AddressException e) {
          throw new IllegalArgumentException("Alias \"" + alias + "\" is not a valid email address.");
        }
      }
      normalized.add(trimmed);
    }
    return new ArrayList<>(normalized);
  }
}
