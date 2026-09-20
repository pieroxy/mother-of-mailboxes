package net.pieroxy.imf.config.general;

import net.pieroxy.imf.utils.CredentialsResolver;

public class WebServerConfiguration {
  private boolean enabled;
  private int httpPort;
  private String address;
  /** Key into credentials.json's top-level "credentials" map — see {@link CredentialsResolver}. */
  private String credentials;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public int getHttpPort() {
    return httpPort;
  }

  public void setHttpPort(int httpPort) {
    this.httpPort = httpPort;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public String getCredentials() {
    return credentials;
  }

  public void setCredentials(String credentials) {
    this.credentials = credentials;
  }
}
