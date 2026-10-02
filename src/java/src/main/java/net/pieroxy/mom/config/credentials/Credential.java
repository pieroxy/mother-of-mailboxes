package net.pieroxy.mom.config.credentials;

/**
 * {@code password} is plaintext — required for a mail account's IMAP login, which needs the real
 * password to authenticate. {@code passwordHash} is only ever populated for the web server's own
 * login credential (see {@code Runner#main}, {@code PasswordHasher}): once set, {@code password}
 * is cleared, so the web login's password never sits in credentials.json in plaintext.
 * {@code temporary} is also web-login only: while set, every authenticated API call except
 * {@code ChangePasswordApi} is refused, so the first thing a login can do is pick a new password.
 */
public class Credential {
  private String username;
  private String password;
  private PasswordHash passwordHash;
  private boolean temporary;

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

  public boolean isTemporary() {
    return temporary;
  }

  public void setTemporary(boolean temporary) {
    this.temporary = temporary;
  }
}
