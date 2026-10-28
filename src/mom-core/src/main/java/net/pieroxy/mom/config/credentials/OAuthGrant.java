package net.pieroxy.mom.config.credentials;

/**
 * A mail login authorized by signing in with the provider (see {@code MicrosoftIdentity}) rather
 * than with a password. The access token and its expiry are only kept in memory.
 */
public class OAuthGrant {
  private OAuthProvider provider;
  /** The user's own app registration with the provider. */
  private String clientId;
  /** Replaced as the provider rotates it: see {@link #setOnRefreshTokenRotated}. */
  private String refreshToken;
  private transient String accessToken;
  private transient long accessTokenExpiresAtMs;
  private transient Runnable onRefreshTokenRotated;

  public OAuthGrant() {
  }

  public OAuthGrant(OAuthProvider provider, String clientId, String refreshToken) {
    this.provider = provider;
    this.clientId = clientId;
    this.refreshToken = refreshToken;
  }

  public OAuthProvider getProvider() {
    return provider;
  }

  public void setProvider(OAuthProvider provider) {
    this.provider = provider;
  }

  public String getClientId() {
    return clientId;
  }

  public void setClientId(String clientId) {
    this.clientId = clientId;
  }

  public String getRefreshToken() {
    return refreshToken;
  }

  /** Calls the {@link #setOnRefreshTokenRotated} callback when it changes. */
  public void setRefreshToken(String refreshToken) {
    boolean rotated = this.refreshToken != null && !this.refreshToken.equals(refreshToken);
    this.refreshToken = refreshToken;
    if (rotated && onRefreshTokenRotated != null) onRefreshTokenRotated.run();
  }

  public String getAccessToken() {
    return accessToken;
  }

  public long getAccessTokenExpiresAtMs() {
    return accessTokenExpiresAtMs;
  }

  public void setAccessToken(String accessToken, long expiresAtMs) {
    this.accessToken = accessToken;
    this.accessTokenExpiresAtMs = expiresAtMs;
  }

  /** Persists credentials.json for a grant that lives there. */
  public void setOnRefreshTokenRotated(Runnable onRefreshTokenRotated) {
    this.onRefreshTokenRotated = onRefreshTokenRotated;
  }
}
