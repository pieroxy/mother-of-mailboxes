package net.pieroxy.mom.api;

import net.pieroxy.mom.config.credentials.Credential;
import net.pieroxy.mom.rules.MailAccount;

import java.util.List;

/** Dependencies handed to API endpoints at construction time. */
public class ServiceProvider {
  private final Credential webServerCredential;
  private final SessionStore sessionStore;
  private final List<MailAccount> accounts;

  public ServiceProvider(Credential webServerCredential, SessionStore sessionStore, List<MailAccount> accounts) {
    this.webServerCredential = webServerCredential;
    this.sessionStore = sessionStore;
    this.accounts = accounts;
  }

  public Credential getWebServerCredential() {
    return webServerCredential;
  }

  public SessionStore getSessionStore() {
    return sessionStore;
  }

  /** The accounts running in this process — see {@code Runner#main}. Never mutated by the API layer. */
  public List<MailAccount> getAccounts() {
    return accounts;
  }
}
