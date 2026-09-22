package net.pieroxy.mom.api;

import net.pieroxy.mom.config.credentials.Credential;

/** Dependencies handed to API endpoints at construction time. */
public class ServiceProvider {
  private final Credential webServerCredential;
  private final SessionStore sessionStore;

  public ServiceProvider(Credential webServerCredential, SessionStore sessionStore) {
    this.webServerCredential = webServerCredential;
    this.sessionStore = sessionStore;
  }

  public Credential getWebServerCredential() {
    return webServerCredential;
  }

  public SessionStore getSessionStore() {
    return sessionStore;
  }
}
