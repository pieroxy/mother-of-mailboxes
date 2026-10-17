package net.pieroxy.mom.services;

import net.pieroxy.mom.webserver.TrustedProxies;
import net.pieroxy.mom.utils.logging.LogRetention;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.pieroxy.mom.config.credentials.Credential;
import net.pieroxy.mom.config.credentials.CredentialsFile;
import net.pieroxy.mom.config.credentials.CredentialsFileStore;
import net.pieroxy.mom.config.credentials.PasswordHasher;
import net.pieroxy.mom.config.general.Configuration;
import net.pieroxy.mom.config.general.MailAccountConfiguration;
import net.pieroxy.mom.config.general.MailDiagnosticsConfiguration;
import net.pieroxy.mom.config.general.ReputationListConfig;
import net.pieroxy.mom.config.general.SmtpConfiguration;
import net.pieroxy.mom.detection.reputation.ReputationRegistry;
import net.pieroxy.mom.detection.reputation.ReputationRegistryHolder;
import net.pieroxy.mom.utils.CredentialsResolver;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
  // The resolved data folder this run uses. A change from the settings page only applies after a
  // restart (see updateGeneralSettings); only the setup wizard moves it live (saveSetupStorage).
  private volatile String dataFolder;

  private Credential webServerCredential;
  private volatile TrustedProxies trustedProxies = TrustedProxies.NONE;

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
   * {@link #migrateWebServerPasswordIfNeeded}. Then sweeps credentials.json for entries config.json
   * no longer references — see {@link #cleanUpDanglingCredentials}. Refuses to start on an invalid
   * {@code trustedProxies} entry.
   */
  @Override
  public void start() {
    trustedProxies = TrustedProxies.parse(config.getTrustedProxies());
    if (!trustedProxies.isEmpty()) LOGGER.info("Trusted proxies (X-Forwarded-For believed from): " + String.join(", ", trustedProxies.entries()));
    LOGGER.info(config.getMailDiagnosticsSinkUrl() == null ? "Mail diagnostics: no SMTP sink configured"
        : "Mail diagnostics SMTP sink: " + config.getMailDiagnosticsSinkUrl());
    if (config.getWebServer() != null && config.getWebServer().isEnabled()) {
      webServerCredential = resolveCredential(config.getWebServer().getCredentials(), "webServer");
      migrateWebServerPasswordIfNeeded();
    }
    cleanUpDanglingCredentials();
  }

  public TrustedProxies getTrustedProxies() {
    return trustedProxies;
  }

  public Configuration getConfiguration() {
    return config;
  }

  public String getDataFolder() {
    return dataFolder;
  }

  public boolean isSetupInProgress() {
    return config.isSetupInProgress();
  }

  /**
   * Setup wizard's storage step. The data folder applies right away for whatever is created from
   * now on (accounts, reputation cache); the caller moves the log file.
   *
   * @return the previous resolved data folder if it changed, null otherwise.
   */
  public synchronized String saveSetupStorage(String rawDataFolder, Integer keepLogFiles) {
    requireSetupInProgress();
    if (rawDataFolder == null || rawDataFolder.isBlank()) {
      throw new IllegalArgumentException("The data folder must not be blank.");
    }
    LogRetention.validate(keepLogFiles);
    String previousRaw = config.getDataFolder();
    config.setDataFolder(rawDataFolder.trim());
    String resolved;
    try {
      resolved = config.resolveDataFolder(configFile.getAbsoluteFile().getParentFile());
      Files.createDirectories(Path.of(resolved));
    } catch (IOException | RuntimeException e) {
      config.setDataFolder(previousRaw);
      throw new IllegalArgumentException("Could not create the data folder " + rawDataFolder.trim() + ": " + e.getMessage());
    }
    config.setKeepLogFiles(keepLogFiles);
    persistConfig();
    String previous = dataFolder;
    dataFolder = resolved;
    return previous.equals(resolved) ? null : previous;
  }

  /** Setup wizard's last step: adds the chosen reputation lists (ids already configured are kept as is) and ends the setup. */
  public synchronized void completeSetup(List<ReputationListConfig> listsToAdd) {
    requireSetupInProgress();
    List<ReputationListConfig> lists = config.getReputationLists() != null ? new ArrayList<>(config.getReputationLists()) : new ArrayList<>();
    Set<String> ids = new HashSet<>();
    lists.forEach(l -> ids.add(l.getId()));
    for (ReputationListConfig list : listsToAdd) {
      if (ids.add(list.getId())) lists.add(list);
    }
    config.setReputationLists(lists);
    config.setSetupInProgress(false);
    persistConfig();
    replaceReputationRegistry(lists);
  }

  private void requireSetupInProgress() {
    if (!config.isSetupInProgress()) {
      throw new IllegalStateException("The setup is already complete.");
    }
  }

  /** Null if no {@code webServer} section is configured/enabled — see {@link #start}. */
  public Credential getWebServerCredential() {
    return webServerCredential;
  }

  public boolean isSessionCookieSecure() {
    return config.getWebServer() != null && config.getWebServer().isSecureCookie();
  }

  public boolean isWebServerPasswordTemporary() {
    return webServerCredential != null && webServerCredential.isTemporary();
  }

  /** Replaces the web login's password and clears its {@code temporary} flag. */
  public synchronized void changeWebServerPassword(String newPassword) {
    if (webServerCredential == null) {
      throw new IllegalStateException("The web server has no login credential.");
    }
    if (newPassword == null || newPassword.isBlank()) {
      throw new IllegalArgumentException("The new password must not be blank.");
    }
    if (PasswordHasher.verify(newPassword, webServerCredential.getPasswordHash())) {
      throw new IllegalArgumentException("The new password must differ from the current one.");
    }
    webServerCredential.setPasswordHash(PasswordHasher.hash(newPassword));
    webServerCredential.setPassword(null);
    webServerCredential.setTemporary(false);
    persistCredentialsFile();
  }

  /**
   * @param context human-readable description of the caller, used in the error message (e.g.
   *                {@code "mail account \"personal\""}).
   */
  public Credential resolveCredential(String key, String context) {
    return CredentialsResolver.resolve(key, credentialsFile, context);
  }

  /** The login used to send mail for this account: its own SMTP credential, or else its IMAP one. */
  public Credential resolveSmtpCredential(MailAccountConfiguration account) {
    SmtpConfiguration smtp = account.getSmtp();
    String key = smtp != null && smtp.getCredentials() != null ? smtp.getCredentials() : account.getCredentials();
    return resolveCredential(key, "SMTP settings of mail account \"" + account.getDisplayName() + "\"");
  }

  /**
   * Replaces the account's SMTP settings (null: none), without persisting config.json. With
   * {@code ownCredentials}, the SMTP login gets its own credentials.json entry — created on first
   * use, under a key derived from the account's — and a blank {@code password} keeps the saved
   * one. Without, the account's IMAP login is used and any SMTP-only entry is dropped.
   *
   * @throws IllegalArgumentException if a new SMTP login has a blank password; nothing has changed then.
   */
  public synchronized void setSmtp(MailAccountConfiguration account, SmtpConfiguration smtp, boolean ownCredentials,
                                   String username, String password) {
    String existingKey = account.getSmtp() != null ? account.getSmtp().getCredentials() : null;
    boolean passwordGiven = password != null && !password.isBlank();
    if (smtp != null && ownCredentials && existingKey == null && !passwordGiven) {
      throw new IllegalArgumentException("The SMTP password must not be blank.");
    }
    if (smtp != null && ownCredentials) {
      Map<String, Credential> credentials = credentialsFile.getCredentials() != null
          ? new HashMap<>(credentialsFile.getCredentials()) : new HashMap<>();
      String key = existingKey;
      if (key == null) {
        key = account.getCredentials() + "-smtp";
        for (int i = 2; credentials.containsKey(key); i++) {
          key = account.getCredentials() + "-smtp-" + i;
        }
        credentials.put(key, new Credential());
        credentialsFile.setCredentials(credentials);
      }
      Credential credential = credentials.get(key);
      credential.setUsername(username);
      if (passwordGiven) credential.setPassword(password);
      smtp.setCredentials(key);
      persistCredentialsFile();
    } else if (smtp != null) {
      smtp.setCredentials(null);
    }
    account.setSmtp(smtp);
    if (existingKey != null && (smtp == null || !ownCredentials)) cleanUpDanglingCredentials();
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

  /**
   * Adds a new account to config.json and its IMAP login to credentials.json, under a fresh
   * credentials key derived from the account's displayName, and persists both files.
   */
  public synchronized void addAccountConfig(MailAccountConfiguration accountConfig, String username, String password) {
    Map<String, Credential> credentials = credentialsFile.getCredentials() != null
        ? new HashMap<>(credentialsFile.getCredentials()) : new HashMap<>();
    String key = accountConfig.getDisplayName();
    for (int i = 2; credentials.containsKey(key); i++) {
      key = accountConfig.getDisplayName() + "-" + i;
    }
    Credential credential = new Credential();
    credential.setUsername(username);
    credential.setPassword(password);
    credentials.put(key, credential);
    credentialsFile.setCredentials(credentials);
    accountConfig.setCredentials(key);

    List<MailAccountConfiguration> accounts = config.getConfigurations() != null
        ? new ArrayList<>(config.getConfigurations()) : new ArrayList<>();
    accounts.add(accountConfig);
    config.setConfigurations(accounts);

    persistCredentialsFile();
    persistConfig();
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
   * Removes any credentials.json entry no longer referenced by config.json: each mail account's
   * {@code credentials} key and SMTP one, plus {@code webServer}'s if that section exists at all
   * (regardless of {@code enabled} — disabling it doesn't detach the mapping). Called from
   * {@link #start}, {@link AccountService#deleteAccount} and {@link #setSmtp} — the sole points that
   * can actually leave an entry dangling.
   */
  public synchronized void cleanUpDanglingCredentials() {
    Map<String, Credential> credentials = credentialsFile.getCredentials();
    if (credentials == null || credentials.isEmpty()) return;

    Set<String> referenced = new HashSet<>();
    if (config.getWebServer() != null) referenced.add(config.getWebServer().getCredentials());
    for (MailAccountConfiguration c : config.getConfigurations()) {
      referenced.add(c.getCredentials());
      if (c.getSmtp() != null && c.getSmtp().getCredentials() != null) referenced.add(c.getSmtp().getCredentials());
    }

    Set<String> dangling = new HashSet<>(credentials.keySet());
    dangling.removeAll(referenced);
    if (dangling.isEmpty()) return;

    // A fresh, guaranteed-mutable map rather than mutating credentials in place: nothing
    // guarantees the Map a caller handed CredentialsFile (or Gson, on deserialization) supports
    // removal.
    Map<String, Credential> kept = new HashMap<>(credentials);
    dangling.forEach(kept::remove);
    credentialsFile.setCredentials(kept);
    persistCredentialsFile();
    LOGGER.info("Removed dangling credentials.json entry/entries no longer referenced by config.json: " + dangling);
  }

  /**
   * Applies the general settings page's staged fields. The web server's own login and the whole
   * {@code reputationLists} list take effect immediately (login on the next request; lists via a
   * hot-swapped {@link ReputationRegistry}), as do {@code trustedProxies} and {@code mailDiagnosticsSinkUrl}
   * (null: diagnostics disabled — already normalized, see {@code SmtpSinkClient#normalizeUrl}). {@code dataFolder} and {@code keepLogFiles} are
   * persisted to {@code config.json} but only take effect once the process is restarted by hand.
   */
  public synchronized void updateGeneralSettings(String newDataFolder, Integer keepLogFiles, List<String> trustedProxies,
                                                  String webServerUsername, String webServerPassword,
                                                  List<ReputationListConfig> reputationLists, String mailDiagnosticsSinkUrl) {
    TrustedProxies parsedTrustedProxies = TrustedProxies.parse(trustedProxies);
    config.setDataFolder(newDataFolder);
    config.setTrustedProxies(parsedTrustedProxies.isEmpty() ? null : parsedTrustedProxies.entries());
    this.trustedProxies = parsedTrustedProxies;
    config.setKeepLogFiles(keepLogFiles);
    if (!Objects.equals(config.getMailDiagnosticsSinkUrl(), mailDiagnosticsSinkUrl)) {
      LOGGER.info(mailDiagnosticsSinkUrl == null ? "Mail diagnostics SMTP sink removed: diagnostics disabled"
          : "Mail diagnostics SMTP sink set to " + mailDiagnosticsSinkUrl);
    }
    if (mailDiagnosticsSinkUrl == null) {
      config.setMailDiagnostics(null);
    } else {
      if (config.getMailDiagnostics() == null) config.setMailDiagnostics(new MailDiagnosticsConfiguration());
      config.getMailDiagnostics().setSinkUrl(mailDiagnosticsSinkUrl);
    }

    if (config.getWebServer() != null) {
      if (webServerCredential == null) {
        webServerCredential = resolveCredential(config.getWebServer().getCredentials(), "webServer");
      }
      webServerCredential.setUsername(webServerUsername);
      if (webServerPassword != null && !webServerPassword.isBlank()) {
        webServerCredential.setPasswordHash(PasswordHasher.hash(webServerPassword));
        webServerCredential.setPassword(null);
        webServerCredential.setTemporary(false);
      }
      persistCredentialsFile();
    }

    config.setReputationLists(reputationLists);
    persistConfig();
    replaceReputationRegistry(reputationLists);
  }

  /** Hot-swaps the process-wide registry: lists apply with no restart. */
  private void replaceReputationRegistry(List<ReputationListConfig> reputationLists) {
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
