package net.pieroxy.mom.services;

import net.pieroxy.mom.config.credentials.Credential;
import net.pieroxy.mom.config.general.LearningShortcutConfiguration;
import net.pieroxy.mom.config.general.MailAccountConfiguration;
import net.pieroxy.mom.config.general.MailFilterRuleConfiguration;
import net.pieroxy.mom.rules.MailAccount;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Owns the accounts actually running in this process: builds and starts one {@link MailAccount}
 * per {@code config.json} entry (see {@link #start}), and applies every edit an API endpoint makes
 * that needs one restarted. Not config.json/credentials.json themselves — see
 * {@link SettingsService}, which every persist/credential-lookup here delegates to (via plain
 * constructor injection, not {@link Service#getDependencies}: there's no circularity here to
 * resolve, so there's nothing declaring a dependency would add over just holding the reference).
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
   * Interrupts every account's thread — which ends the wait between cycles and prevents a new one
   * from starting (see {@link MailAccount#requestStop}) — then waits up to
   * {@code SHUTDOWN_JOIN_TIMEOUT_MS}, shared (not per account): accounts die concurrently in the
   * background regardless of which one we're currently join()ing, so budgeting per-account would
   * let a slow one after a fast one add its own full timeout on top for nothing.
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
   * Persists config.json (with whatever in-place edits an endpoint already made via
   * {@link SettingsService#findAccountConfig}), then stops the account's current thread and
   * starts a fresh one built from the updated configuration, replacing it in {@link #getAccounts()}
   * at the same position. {@code synchronized}: this is rare and slow (an IMAP round trip, a full
   * account stop/start) compared to every other read-only API call, so a single coarse lock across
   * all accounts is simpler than per-account locking and costs nothing in practice.
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
}
