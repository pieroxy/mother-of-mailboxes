package net.pieroxy.mom.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.pieroxy.mom.config.credentials.Credential;
import net.pieroxy.mom.config.credentials.CredentialsFile;
import net.pieroxy.mom.config.general.Configuration;
import net.pieroxy.mom.config.general.MailAccountConfiguration;
import net.pieroxy.mom.config.general.LearningShortcutConfiguration;
import net.pieroxy.mom.config.general.MailFilterRuleConfiguration;
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

  private void persistConfig() {
    try (Writer w = new FileWriter(configFile)) {
      GSON.toJson(config, w);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not write " + configFile, e);
    }
  }

  private void persistCredentialsFile() {
    try (Writer w = new FileWriter(credentialsFilePath)) {
      GSON.toJson(credentialsFile, w);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not write " + credentialsFilePath, e);
    }
  }
}
