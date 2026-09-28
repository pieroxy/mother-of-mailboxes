package net.pieroxy.mom.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.pieroxy.mom.config.credentials.Credential;
import net.pieroxy.mom.config.credentials.CredentialsFile;
import net.pieroxy.mom.config.credentials.CredentialsFileStore;
import net.pieroxy.mom.config.credentials.PasswordHasher;
import net.pieroxy.mom.config.general.Configuration;
import net.pieroxy.mom.config.general.MailAccountConfiguration;
import net.pieroxy.mom.config.general.ReputationListConfig;
import net.pieroxy.mom.detection.reputation.ReputationRegistry;
import net.pieroxy.mom.detection.reputation.ReputationRegistryHolder;
import net.pieroxy.mom.utils.CredentialsResolver;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns config.json and credentials.json: the live {@link Configuration}, and read/write access to
 * the credentials map, for whichever part of the API layer needs to read or persist them
 * (GeneralSettingsApi/UpdateGeneralSettingsApi directly, {@link AccountService} for each mail
 * account's own credential). Not the running accounts themselves — see {@link AccountService}.
 */
public class SettingsService implements Service {
  private final static Logger LOGGER = Logger.getLogger(SettingsService.class.getName());
  private final static Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  private final Configuration config;
  private final File configFile;
  private final CredentialsFile credentialsFile;
  private final File credentialsFilePath;
  // A saved change to dataFolder only takes effect once the whole process is restarted by hand
  // (see updateGeneralSettings) — captured once here, rather than re-read from config after an
  // edit, so anything that touches disk this run keeps using the value the process started with.
  private final String dataFolder;

  private Credential webServerCredential;

  public SettingsService(Configuration config, File configFile, CredentialsFile credentialsFile,
                          File credentialsFilePath, String dataFolder) {
    this.config = config;
    this.configFile = configFile;
    this.credentialsFile = credentialsFile;
    this.credentialsFilePath = credentialsFilePath;
    this.dataFolder = dataFolder;
  }

  /**
   * Resolves the web server's login credential (if {@code webServer} is configured and enabled)
   * and migrates a plaintext password to a hashed one — see
   * {@link #migrateWebServerPasswordIfNeeded}.
   */
  @Override
  public void start() {
    if (config.getWebServer() != null && config.getWebServer().isEnabled()) {
      webServerCredential = resolveCredential(config.getWebServer().getCredentials(), "webServer");
      migrateWebServerPasswordIfNeeded();
    }
  }

  public Configuration getConfiguration() {
    return config;
  }

  public String getDataFolder() {
    return dataFolder;
  }

  /** Null if no {@code webServer} section is configured/enabled — see {@link #start}. */
  public Credential getWebServerCredential() {
    return webServerCredential;
  }

  /**
   * @param context human-readable description of the caller, used in the error message (e.g.
   *                {@code "mail account \"personal\""}).
   */
  public Credential resolveCredential(String key, String context) {
    return CredentialsResolver.resolve(key, credentialsFile, context);
  }

  /**
   * The account's own {@link MailAccountConfiguration}, live from the in-memory {@link Configuration}
   * (not a copy): an API endpoint mutates it in place via its setters, then calls
   * {@code AccountService#restartAccount} to persist the change and apply it.
   */
  public MailAccountConfiguration findAccountConfig(String accountName) {
    return config.getConfigurations().stream()
        .filter(c -> accountName.equals(c.getDisplayName()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("No such account: " + accountName));
  }

  /** Removes one account's entry from the in-memory config — see {@link AccountService#deleteAccount}. */
  public void removeAccountConfig(String accountName) {
    boolean removed = config.getConfigurations().removeIf(c -> accountName.equals(c.getDisplayName()));
    if (!removed) {
      throw new IllegalArgumentException("No such account: " + accountName);
    }
  }

  public void persistConfig() {
    try (Writer w = new FileWriter(configFile)) {
      GSON.toJson(config, w);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not write " + configFile, e);
    }
  }

  public void persistCredentialsFile() {
    try {
      CredentialsFileStore.save(credentialsFilePath, credentialsFile);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not write " + credentialsFilePath, e);
    }
  }

  /**
   * Applies every field the general settings page offers. The web server's own login and the
   * whole {@code reputationLists} list take effect immediately (login on the next request; lists
   * via a hot-swapped {@link ReputationRegistry}). {@code dataFolder}, {@code keepLogFiles} and
   * the web server's own connection settings are persisted to {@code config.json} but only take
   * effect once the process is restarted by hand.
   */
  public synchronized void updateGeneralSettings(String newDataFolder, int keepLogFiles, boolean webServerEnabled,
                                                  int webServerHttpPort, String webServerAddress,
                                                  String webServerUsername, String webServerPassword,
                                                  List<ReputationListConfig> reputationLists) {
    config.setDataFolder(newDataFolder);
    config.setKeepLogFiles(keepLogFiles);

    if (config.getWebServer() != null) {
      config.getWebServer().setEnabled(webServerEnabled);
      config.getWebServer().setHttpPort(webServerHttpPort);
      config.getWebServer().setAddress(webServerAddress);

      if (webServerCredential == null) {
        webServerCredential = resolveCredential(config.getWebServer().getCredentials(), "webServer");
      }
      webServerCredential.setUsername(webServerUsername);
      if (webServerPassword != null && !webServerPassword.isBlank()) {
        webServerCredential.setPasswordHash(PasswordHasher.hash(webServerPassword));
        webServerCredential.setPassword(null);
      }
      persistCredentialsFile();
    }

    config.setReputationLists(reputationLists);
    persistConfig();

    ReputationRegistry old = ReputationRegistryHolder.get();
    ReputationRegistry fresh = new ReputationRegistry(reputationLists, dataFolder);
    fresh.start();
    ReputationRegistryHolder.set(fresh);
    old.stop();
  }

  /**
   * If the credential still has a plaintext password and no hash, hashes it, clears the
   * plaintext, and persists credentials.json. A write failure is logged and skipped rather than
   * blocking startup; the in-memory hash still works this run, and migration retries next boot.
   */
  private void migrateWebServerPasswordIfNeeded() {
    if (webServerCredential.getPassword() == null || webServerCredential.getPasswordHash() != null) return;
    webServerCredential.setPasswordHash(PasswordHasher.hash(webServerCredential.getPassword()));
    webServerCredential.setPassword(null);
    try {
      persistCredentialsFile();
      LOGGER.info("webServer credential: migrated plaintext password to a hashed one in " + credentialsFilePath);
    } catch (UncheckedIOException e) {
      LOGGER.log(Level.WARNING, "webServer credential: hashed the password but could not persist " + credentialsFilePath
          + " — will retry on next startup", e.getCause());
    }
  }
}
