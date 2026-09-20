package net.pieroxy.imf.api;

import net.pieroxy.imf.config.credentials.Credential;

/** Dependencies handed to API endpoints at construction time. */
public class ServiceProvider {
  private final Credential webServerCredential;

  public ServiceProvider(Credential webServerCredential) {
    this.webServerCredential = webServerCredential;
  }

  public Credential getWebServerCredential() {
    return webServerCredential;
  }
}
