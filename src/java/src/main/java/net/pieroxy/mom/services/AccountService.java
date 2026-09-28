package net.pieroxy.mom.services;

import net.pieroxy.mom.config.credentials.Credential;
import net.pieroxy.mom.config.general.LearningShortcutConfiguration;
import net.pieroxy.mom.config.general.MailAccountConfiguration;
import net.pieroxy.mom.config.general.MailFilterRuleConfiguration;
import net.pieroxy.mom.rules.MailAccount;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Owns the accounts running in this process: builds and starts one {@link MailAccount} per
 * {@code config.json} entry (see {@link #start}), and applies edits that need one restarted.
 * Delegates all config/credential persistence to {@link SettingsService}.
 */
public class AccountService implements Service {
  // Generous: an in-progress IMAP cycle (blocking socket I/O) won't be interrupted on the spot —
  // see MailAccount#requestStop — so a restart/shutdown during a slow cycle needs real room to
  // finish it.
  private final static long RESTART_JOIN_TIMEOUT_MS = 10_000;
  private final static long SHUTDOWN_JOIN_TIMEOUT_MS = 5_000;

  private final SettingsService settingsService;
  private final List<MailAccount> accounts;

  public AccountService(SettingsService settingsService) {
    this(settingsService, new CopyOnWriteArrayList<>());
  }

  /**
   * Starts from an already-built list instead of letting {@link #start()} build one — e.g. a test
   * seeding a single controlled {@link MailAccount} that never dials out for real.
   */
  public AccountService(SettingsService settingsService, List<MailAccount> accounts) {
    this.settingsService = settingsService;
    this.accounts = accounts;
  }

  @Override
  public void start() {
    settingsService.getConfiguration().getConfigurations().forEach(conf -> {
      Credential credential = settingsService.resolveCredential(conf.getCredentials(), "mail account \"" + conf.getDisplayName() + "\"");
      MailAccount account = new MailAccount(conf, credential, settingsService.getDataFolder());
      accounts.add(account);
      account.start();
    });
  }

  /**
   * Interrupts every account's thread (see {@link MailAccount#requestStop}), then waits up to a
   * shared {@code SHUTDOWN_JOIN_TIMEOUT_MS} for all of them, not per account.
   */
  @Override
  public void destroy() {
    accounts.forEach(MailAccount::requestStop);
    long deadline = System.currentTimeMillis() + SHUTDOWN_JOIN_TIMEOUT_MS;
    for (MailAccount account : accounts) {
      try {
        long remainingMs = deadline - System.currentTimeMillis();
        if (remainingMs > 0) account.join(remainingMs);
      } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
      }
    }
  }

  /** The accounts running in this process. Mutated only by {@link #restartAccount}. */
  public List<MailAccount> getAccounts() {
    return accounts;
  }

  /**
   * Persists config.json, then stops the account's current thread and starts a fresh one built
   * from the updated configuration, replacing it in {@link #getAccounts()} at the same position.
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

    settingsService.persistConfig();

    MailAccount old = accounts.get(index);
    old.requestStop();
    try {
      old.join(RESTART_JOIN_TIMEOUT_MS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    MailAccountConfiguration updatedConfig = settingsService.findAccountConfig(accountName);
    Credential credential = settingsService.resolveCredential(updatedConfig.getCredentials(), "mail account \"" + accountName + "\"");
    MailAccount fresh = new MailAccount(updatedConfig, credential, settingsService.getDataFolder());
    accounts.set(index, fresh);
    fresh.start();
  }

  /**
   * Applies every editable part of an account's configuration — Config fields, IMAP credentials,
   * rules and learning shortcuts — and restarts it once. {@code displayName} isn't settable here
   * (it names the account's on-disk files). A blank {@code password} leaves the current one
   * unchanged.
   */
  public synchronized void updateAccount(String accountName, String host, int port, int runEvery,
                                          String classifierSpamFolderName, List<String> classifierExcludedFolders,
                                          int classifierCorpusRetentionDays, int classifierCorpusScanBatchSize,
                                          boolean discoveryTreeDisabled, String username, String password,
                                          List<MailFilterRuleConfiguration> rules,
                                          List<LearningShortcutConfiguration> shortcuts) {
    MailAccountConfiguration config = settingsService.findAccountConfig(accountName);
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

    Credential credential = settingsService.resolveCredential(config.getCredentials(), "mail account \"" + accountName + "\"");
    credential.setUsername(username);
    if (password != null && !password.isBlank()) {
      credential.setPassword(password);
    }
    settingsService.persistCredentialsFile();

    restartAccount(accountName);
  }

  /**
   * Permanently removes one account: stops its thread, drops it from {@link #getAccounts()}, and
   * removes its entry from config.json. Deliberately leaves its credentials.json entry and any
   * on-disk state (learned rules, stats, classifier corpus) untouched — nothing else references
   * them once the account is gone, but silently deleting a user's history as a side effect of
   * removing a config entry would be a surprise, not a convenience.
   */
  public synchronized void deleteAccount(String accountName) {
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

    MailAccount account = accounts.get(index);
    account.requestStop();
    try {
      account.join(RESTART_JOIN_TIMEOUT_MS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    accounts.remove(index);

    settingsService.removeAccountConfig(accountName);
    settingsService.persistConfig();
  }

  /**
   * Replaces one account's learned rules and applies the change immediately (see
   * {@link MailAccount#updateLearnedRules}) — no restart, since learned rules live in their own
   * file, not config.json.
   */
  public synchronized void updateLearnedRules(String accountName, List<MailFilterRuleConfiguration> rules) {
    MailAccount account = accounts.stream()
        .filter(a -> a.getAccountLabel().equals(accountName))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("No such account: " + accountName));
    account.updateLearnedRules(rules);
  }
}
