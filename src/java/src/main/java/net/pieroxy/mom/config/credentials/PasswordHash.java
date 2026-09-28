package net.pieroxy.mom.config.credentials;

/**
 * A salted, iterated hash of the web login's password (see {@link PasswordHasher}) — never
 * applied to a mail account's IMAP {@link Credential#getPassword()}, which must stay plaintext
 * for the server to actually authenticate with it. {@code algo}/{@code iterations} are stored
 * alongside the salt/hash themselves (not assumed from code) so a future change to either doesn't
 * invalidate credentials.json entries hashed under the old parameters.
 */
public class PasswordHash {
  private String algo;
  private int iterations;
  /** Base64. */
  private String salt;
  /** Base64. */
  private String hash;

  public String getAlgo() {
    return algo;
  }

  public void setAlgo(String algo) {
    this.algo = algo;
  }

  public int getIterations() {
    return iterations;
  }

  public void setIterations(int iterations) {
    this.iterations = iterations;
  }

  public String getSalt() {
    return salt;
  }

  public void setSalt(String salt) {
    this.salt = salt;
  }

  public String getHash() {
    return hash;
  }

  public void setHash(String hash) {
    this.hash = hash;
  }
}
