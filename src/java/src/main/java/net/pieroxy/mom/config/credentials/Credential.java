package net.pieroxy.mom.config.credentials;

/**
 * {@code password} is plaintext — required for a mail account's IMAP login, which needs the real
 * password to authenticate. {@code passwordHash} is only ever populated for the web server's own
 * login credential (see {@code Runner#main}, {@code PasswordHasher}): once set, {@code password}
 * is cleared, so the web login's password never sits in credentials.json in plaintext.
 */
public class Credential {
  private String username;
  private String password;
  private PasswordHash passwordHash;

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

  public PasswordHash getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(PasswordHash passwordHash) {
    this.passwordHash = passwordHash;
  }
}
