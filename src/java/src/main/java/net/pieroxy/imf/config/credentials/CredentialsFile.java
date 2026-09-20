package net.pieroxy.imf.config.credentials;

import java.util.Map;

public class CredentialsFile {
  private Map<String, Credential> credentials;

  public Map<String, Credential> getCredentials() {
    return credentials;
  }

  public void setCredentials(Map<String, Credential> credentials) {
    this.credentials = credentials;
  }
}
