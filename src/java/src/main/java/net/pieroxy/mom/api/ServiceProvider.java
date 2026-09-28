package net.pieroxy.mom.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.pieroxy.mom.config.credentials.Credential;
import net.pieroxy.mom.config.credentials.CredentialsFile;
import net.pieroxy.mom.config.credentials.CredentialsFileStore;
import net.pieroxy.mom.config.credentials.PasswordHasher;
import net.pieroxy.mom.config.general.Configuration;
import net.pieroxy.mom.config.general.MailAccountConfiguration;
import net.pieroxy.mom.config.general.LearningShortcutConfiguration;
import net.pieroxy.mom.config.general.MailFilterRuleConfiguration;
import net.pieroxy.mom.config.general.ReputationListConfig;
import net.pieroxy.mom.detection.reputation.ReputationRegistry;
import net.pieroxy.mom.detection.reputation.ReputationRegistryHolder;
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
  private final File credentialsFilePath;
  private final String dataFolder;

  public ServiceProvider(Credential webServerCredential, SessionStore sessionStore, List<MailAccount> accounts,
                          Configuration config, File configFile, CredentialsFile credentialsFile,
                          File credentialsFilePath, String dataFolder) {
    this.webServerCredential = webServerCredential;
    this.sessionStore = sessionStore;
    this.accounts = accounts;
    this.config = config;
    this.configFile = configFile;
    this.credentialsFile = credentialsFile;
    this.credentialsFilePath = credentialsFilePath;
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
   * The whole live config, for the parts of it that aren't per-account. {@code GeneralSettingsApi}
   * reads it directly; edits go through {@link #updateGeneralSettings} instead, since
   * {@code dataFolder}, {@code keepLogFiles} and {@code webServer}'s own connection settings are
   * startup-time-only — saving a change to them persists to disk immediately but only actually
   * takes effect once the whole process is restarted (by hand; nothing in this webapp can trigger
   * that itself).
   */
  public Configuration getConfiguration() {
    return config;
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

  /**
   * Applies every editable part of an account's configuration — the Config fields, the IMAP
   * credentials, the whole {@code rules} list and the whole {@code learningShortcuts} list — and
   * restarts it exactly once. The webapp stages all of these client-side (see
   * {@code AccountEditSession.ts}) while the user reviews changes across the
   * Config/Credentials/Rules/Shortcuts edit pages, and only calls this, via
   * {@code UpdateAccountApi}, when they click "Save Changes" — so a single restart always covers
   * the whole batch instead of one per section. {@code displayName} is deliberately not
   * settable here: it names every one of this account's on-disk files (state, learned rules,
   * classifier corpus, stats — see {@code FileNameValidator}'s callers), so renaming it would
   * silently orphan that history instead of migrating it. A blank {@code password} leaves the
   * current one unchanged, the same convention {@code CredentialsInfoDto} relies on (the browser
   * is never shown the real password to begin with, so "unchanged" is the only thing a blank
   * field can mean here).
   */
  public synchronized void updateAccount(String accountName, String host, int port, int runEvery,
                                          String classifierSpamFolderName, List<String> classifierExcludedFolders,
                                          int classifierCorpusRetentionDays, int classifierCorpusScanBatchSize,
                                          boolean discoveryTreeDisabled, String username, String password,
                                          List<MailFilterRuleConfiguration> rules,
                                          List<LearningShortcutConfiguration> shortcuts) {
    MailAccountConfiguration config = findAccountConfig(accountName);
    config.setHost(host);
    config.setPort(port);
    config.setRunEvery(runEvery);
    config.setClassifierSpamFolderName(classifierSpamFolderName);
    config.setClassifierExcludedFolders(classifierExcludedFolders != null ? classifierExcludedFolders : List.of());
    config.setClassifierCorpusRetentionDays(classifierCorpusRetentionDays);
    config.setClassifierCorpusScanBatchSize(classifierCorpusScanBatchSize);
    config.setDiscoveryTreeDisabled(discoveryTreeDisabled);
    config.setRules(rules);
    config.setLearningShortcuts(shortcuts);

    Credential credential = CredentialsResolver.resolve(config.getCredentials(), credentialsFile,
        "mail account \"" + accountName + "\"");
    credential.setUsername(username);
    if (password != null && !password.isBlank()) {
      credential.setPassword(password);
    }
    persistCredentialsFile();

    restartAccount(accountName);
  }

  /**
   * Replaces one account's learned rules and applies the change right away (see
   * {@link MailAccount#updateLearnedRules}) — deliberately separate from {@link #updateAccount},
   * and not folded into it: learned rules live in their own per-account file, not config.json, and
   * applying an edit needs no restart, so batching it with a config/credentials/rules/shortcuts
   * save would only cost an unnecessary IMAP reconnect.
   */
  public synchronized void updateLearnedRules(String accountName, List<MailFilterRuleConfiguration> rules) {
    MailAccount account = accounts.stream()
        .filter(a -> a.getAccountLabel().equals(accountName))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("No such account: " + accountName));
    account.updateLearnedRules(rules);
  }

  /**
   * Applies every field the general settings page offers. The web server's own login (its
   * {@code Credential}, resolved and mutated in place, same "blank password = unchanged"
   * convention as an account's) and the whole {@code reputationLists} list take effect immediately
   * — the login on the next request, the lists via a hot-swapped {@link ReputationRegistry} (built
   * — cheap, disk-cache only, no network I/O — then started before the old one is stopped, so
   * there's no window with no registry at all). {@code dataFolder}, {@code keepLogFiles} and the
   * web server's own connection settings ({@code enabled}/{@code httpPort}/{@code address}) are
   * only ever persisted to {@code config.json} here: this process keeps using the values it
   * started with (see the {@code dataFolder} field) until someone restarts it by hand.
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

      Credential credential = CredentialsResolver.resolve(config.getWebServer().getCredentials(), credentialsFile, "webServer");
      credential.setUsername(webServerUsername);
      if (webServerPassword != null && !webServerPassword.isBlank()) {
        credential.setPasswordHash(PasswordHasher.hash(webServerPassword));
        credential.setPassword(null);
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

  private void persistConfig() {
    try (Writer w = new FileWriter(configFile)) {
      GSON.toJson(config, w);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not write " + configFile, e);
    }
  }

  private void persistCredentialsFile() {
    try {
      CredentialsFileStore.save(credentialsFilePath, credentialsFile);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not write " + credentialsFilePath, e);
    }
  }
}
