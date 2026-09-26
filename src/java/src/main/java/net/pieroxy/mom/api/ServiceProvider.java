package net.pieroxy.mom.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.pieroxy.mom.config.credentials.Credential;
import net.pieroxy.mom.config.credentials.CredentialsFile;
import net.pieroxy.mom.config.general.Configuration;
import net.pieroxy.mom.config.general.MailAccountConfiguration;
import net.pieroxy.mom.rules.MailAccount;
import net.pieroxy.mom.utils.CredentialsResolver;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.List;

/** Dependencies handed to API endpoints at construction time. */
public class ServiceProvider {
  private final static Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  // Generous: an in-progress IMAP cycle (blocking socket I/O) won't be interrupted on the spot —
  // see MailAccount#requestStop — so a save during a slow cycle needs real room to finish it.
  private final static long RESTART_JOIN_TIMEOUT_MS = 10_000;

  private final Credential webServerCredential;
  private final SessionStore sessionStore;
  private final List<MailAccount> accounts;
  private final Configuration config;
  private final File configFile;
  private final CredentialsFile credentialsFile;
  private final String dataFolder;

  public ServiceProvider(Credential webServerCredential, SessionStore sessionStore, List<MailAccount> accounts,
                          Configuration config, File configFile, CredentialsFile credentialsFile, String dataFolder) {
    this.webServerCredential = webServerCredential;
    this.sessionStore = sessionStore;
    this.accounts = accounts;
    this.config = config;
    this.configFile = configFile;
    this.credentialsFile = credentialsFile;
    this.dataFolder = dataFolder;
  }

  public Credential getWebServerCredential() {
    return webServerCredential;
  }

  public SessionStore getSessionStore() {
    return sessionStore;
  }

  /** The accounts running in this process — see {@code Runner#main}. Mutated only by {@link #restartAccount}. */
  public List<MailAccount> getAccounts() {
    return accounts;
  }

  /**
   * The account's own {@link MailAccountConfiguration}, live from the in-memory {@link Configuration}
   * (not a copy): an API endpoint mutates it in place via its setters, then calls
   * {@link #restartAccount} to persist the change and apply it.
   */
  public MailAccountConfiguration findAccountConfig(String accountName) {
    return config.getConfigurations().stream()
        .filter(c -> accountName.equals(c.getDisplayName()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("No such account: " + accountName));
  }

  /**
   * Persists {@code config.json} (with whatever in-place edits an endpoint already made via
   * {@link #findAccountConfig}), then stops the account's current thread and starts a fresh one
   * built from the updated configuration, replacing it in {@link #getAccounts()} at the same
   * position. {@code synchronized}: this is rare and slow (an IMAP round trip, a full account
   * stop/start) compared to every other read-only API call, so a single coarse lock across all
   * accounts is simpler than per-account locking and costs nothing in practice.
   */
  public synchronized void restartAccount(String accountName) {
    int index = -1;
    for (int i = 0; i < accounts.size(); i++) {
      if (accounts.get(i).getAccountLabel().equals(accountName)) {
        index = i;
        break;
      }
    }
    if (index < 0) {
      throw new IllegalArgumentException("No such account: " + accountName);
    }

    persistConfig();

    MailAccount old = accounts.get(index);
    old.requestStop();
    try {
      old.join(RESTART_JOIN_TIMEOUT_MS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    MailAccountConfiguration updatedConfig = findAccountConfig(accountName);
    Credential credential = CredentialsResolver.resolve(updatedConfig.getCredentials(), credentialsFile,
        "mail account \"" + accountName + "\"");
    MailAccount fresh = new MailAccount(updatedConfig, credential, dataFolder);
    accounts.set(index, fresh);
    fresh.start();
  }

  private void persistConfig() {
    try (Writer w = new FileWriter(configFile)) {
      GSON.toJson(config, w);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not write " + configFile, e);
    }
  }
}
