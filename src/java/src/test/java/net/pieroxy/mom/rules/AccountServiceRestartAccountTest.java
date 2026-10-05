package net.pieroxy.mom.rules;

import com.google.gson.Gson;
import net.pieroxy.mom.services.AccountService;
import net.pieroxy.mom.services.SettingsService;
import net.pieroxy.mom.config.credentials.Credential;
import net.pieroxy.mom.config.credentials.CredentialsFile;
import net.pieroxy.mom.config.general.Configuration;
import net.pieroxy.mom.config.general.LearningShortcutConfiguration;
import net.pieroxy.mom.config.general.MailAccountConfiguration;
import net.pieroxy.mom.config.general.MailFilterRuleActionConfiguration;
import net.pieroxy.mom.config.general.MailFilterRuleConfiguration;
import net.pieroxy.mom.config.general.MailFilterRuleMatcherConfiguration;
import net.pieroxy.mom.rules.actions.ActionType;
import net.pieroxy.mom.rules.matchers.MatcherType;
import net.pieroxy.mom.utils.mail.GreenMailImapFixture;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

/**
 * {@link AccountService#restartAccount}/{@link AccountService#updateAccount} are exercised here,
 * in {@code net.pieroxy.mom.rules} rather than {@code net.pieroxy.mom.services} (where
 * {@link AccountService} itself lives), only to reach {@link MailAccount}'s package-private test
 * constructor for the *original* account (so it never dials out for real) — both methods under
 * test are still the public {@link AccountService} API.
 */
public class AccountServiceRestartAccountTest {
  private static final String CREDENTIALS_KEY = "test-cred";

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private final GreenMailImapFixture fixture = new GreenMailImapFixture();

  @Before
  public void startServer() {
    fixture.start();
  }

  @After
  public void stopServer() {
    fixture.stop();
  }

  /** Everything a test needs: the live config object to mutate, and the AccountService under test. */
  private static final class Setup {
    final MailAccountConfiguration config;
    final File configFile;
    final File credentialsFilePath;
    final List<MailAccount> accounts;
    final SettingsService settingsService;
    final AccountService accountService;

    Setup(MailAccountConfiguration config, File configFile, File credentialsFilePath, List<MailAccount> accounts,
          SettingsService settingsService, AccountService accountService) {
      this.config = config;
      this.configFile = configFile;
      this.credentialsFilePath = credentialsFilePath;
      this.accounts = accounts;
      this.settingsService = settingsService;
      this.accountService = accountService;
    }
  }

  /**
   * The test-injectable constructor: the original MailAccount instance must never actually dial
   * out — only the account restartAccount() builds afterward (via the real 3-arg constructor)
   * does, and that one fails fast against GreenMail's plain IMAP port (production always asks for
   * "imaps") rather than hanging, so it's safe to just stop it again at the end of each test.
   */
  private Setup setUp() {
    MailAccountConfiguration config = fixture.accountConfig("test-account");
    config.setCredentials(CREDENTIALS_KEY);
    Credential credential = fixture.accountCredential();

    CredentialsFile credentialsFile = new CredentialsFile();
    credentialsFile.setCredentials(Map.of(CREDENTIALS_KEY, credential));

    Configuration configuration = new Configuration();
    configuration.setConfigurations(new ArrayList<>(List.of(config)));
    String dataFolder = tmp.getRoot().getAbsolutePath();
    File configFile = new File(tmp.getRoot(), "config.json");
    File credentialsFilePath = new File(tmp.getRoot(), "credentials.json");

    MailAccount original = new MailAccount(config, credential, dataFolder, (c, cred) -> fixture.connectAsImapMailbox());
    List<MailAccount> accounts = new CopyOnWriteArrayList<>(List.of(original));

    SettingsService settingsService = new SettingsService(configuration, configFile, credentialsFile, credentialsFilePath, dataFolder);
    AccountService accountService = new AccountService(settingsService, accounts);

    return new Setup(config, configFile, credentialsFilePath, accounts, settingsService, accountService);
  }

  /** Stops whatever real account restartAccount()/updateCredentials() started, so it doesn't keep retrying a doomed IMAPS handshake in the background after the test ends. */
  private void stopReplacedAccount(Setup setup) throws InterruptedException {
    MailAccount current = setup.accounts.get(0);
    current.requestStop();
    current.join(2000);
  }

  @Test
  public void restartAccountPersistsTheEditAndReplacesTheRunningInstance() throws Exception {
    Setup setup = setUp();
    MailAccount original = setup.accounts.get(0);

    MailAccountConfiguration liveConfig = setup.settingsService.findAccountConfig("test-account");
    assertSame("findAccountConfig must hand back the live object, not a copy, since callers mutate it in place",
        setup.config, liveConfig);
    liveConfig.setRunEvery(120);

    try {
      setup.accountService.restartAccount("test-account");

      Configuration reloaded = new Gson().fromJson(new FileReader(setup.configFile), Configuration.class);
      assertEquals("the edit must be on disk", 120, reloaded.getConfigurations().get(0).getRunEvery());

      MailAccount replaced = setup.accounts.get(0);
      assertNotSame("the old instance must be replaced, not just mutated", original, replaced);
      assertEquals("the new instance must be built from the updated config", 120, replaced.getConfig().getRunEvery());
    } finally {
      stopReplacedAccount(setup);
    }
  }

  @Test
  public void updateAccountChangesUsernameAndPasswordAndRestarts() throws Exception {
    Setup setup = setUp();
    MailAccount original = setup.accounts.get(0);

    try {
      updateAccount(setup, "new-username", "new-password", List.of());

      CredentialsFile reloaded = new Gson().fromJson(new FileReader(setup.credentialsFilePath), CredentialsFile.class);
      Credential persisted = reloaded.getCredentials().get(CREDENTIALS_KEY);
      assertEquals("username must be on disk", "new-username", persisted.getUsername());
      assertEquals("password must be on disk", "new-password", persisted.getPassword());

      assertNotSame("the account must have been restarted", original, setup.accounts.get(0));
    } finally {
      stopReplacedAccount(setup);
    }
  }

  @Test
  public void updateAccountLeavesThePasswordUnchangedWhenBlank() throws Exception {
    Setup setup = setUp();

    try {
      updateAccount(setup, "new-username", "", List.of());

      CredentialsFile reloaded = new Gson().fromJson(new FileReader(setup.credentialsFilePath), CredentialsFile.class);
      Credential persisted = reloaded.getCredentials().get(CREDENTIALS_KEY);
      assertEquals("username must still change", "new-username", persisted.getUsername());
      assertEquals("a blank password must leave the original one untouched",
          fixture.accountCredential().getPassword(), persisted.getPassword());
    } finally {
      stopReplacedAccount(setup);
    }
  }

  @Test
  public void updateAccountReplacesTheRuleOrderAndRestarts() throws Exception {
    Setup setup = setUp();
    MailAccount original = setup.accounts.get(0);
    MailFilterRuleConfiguration first = rule("spam.example.com", "Spam");
    MailFilterRuleConfiguration second = rule("newsletter.example.com", "Newsletter");

    try {
      // The "reorder" a client actually sends: the same rules, swapped.
      updateAccount(setup, "test-user", "", List.of(second, first));

      Configuration reloaded = new Gson().fromJson(new FileReader(setup.configFile), Configuration.class);
      List<MailFilterRuleConfiguration> persistedRules = reloaded.getConfigurations().get(0).getRules();
      assertEquals("newsletter.example.com", persistedRules.get(0).getMatcher().getKey());
      assertEquals("spam.example.com", persistedRules.get(1).getMatcher().getKey());

      assertNotSame("the account must have been restarted", original, setup.accounts.get(0));
    } finally {
      stopReplacedAccount(setup);
    }
  }

  @Test
  public void updateAccountPersistsLearningShortcuts() throws Exception {
    Setup setup = setUp();
    MailAccount original = setup.accounts.get(0);
    LearningShortcutConfiguration shortcut = shortcut("MoveNewsletterToSpam", MatcherType.FROM_DOMAIN_EQUALS, ActionType.MOVE_TO, "Spam");

    try {
      updateAccount(setup, "test-user", "", List.of(), List.of(shortcut));

      Configuration reloaded = new Gson().fromJson(new FileReader(setup.configFile), Configuration.class);
      List<LearningShortcutConfiguration> persisted = reloaded.getConfigurations().get(0).getLearningShortcuts();
      assertEquals(1, persisted.size());
      assertEquals("MoveNewsletterToSpam", persisted.get(0).getName());
      assertEquals(MatcherType.FROM_DOMAIN_EQUALS, persisted.get(0).getMatcher().getType());
      assertEquals("Spam", persisted.get(0).getAction().getKey());

      assertNotSame("the account must have been restarted", original, setup.accounts.get(0));
    } finally {
      stopReplacedAccount(setup);
    }
  }

  @Test
  public void deleteAccountStopsTheAccountAndRemovesItFromConfig() throws Exception {
    Setup setup = setUp();
    MailAccount original = setup.accounts.get(0);

    setup.accountService.deleteAccount("test-account");

    assertEquals("the account must be gone from the running list", 0, setup.accounts.size());
    original.join(2000);

    Configuration reloaded = new Gson().fromJson(new FileReader(setup.configFile), Configuration.class);
    assertEquals("the account must be gone from config.json", 0, reloaded.getConfigurations().size());
  }

  @Test
  public void deleteAccountAlsoRemovesItsNowDanglingCredential() throws Exception {
    Setup setup = setUp();

    setup.accountService.deleteAccount("test-account");

    CredentialsFile reloaded = new Gson().fromJson(new FileReader(setup.credentialsFilePath), CredentialsFile.class);
    assertEquals("nothing references this account's credential anymore, it must be gone too",
        0, reloaded.getCredentials().size());
  }

  @Test(expected = IllegalArgumentException.class)
  public void deleteAccountRejectsAnUnknownAccountName() {
    Setup setup = setUp();
    setup.accountService.deleteAccount("does-not-exist");
  }

  @Test
  public void setAccountActiveFalsePersistsAndStopsTheAccountWithoutRemovingIt() throws Exception {
    Setup setup = setUp();
    MailAccount original = setup.accounts.get(0);

    try {
      setup.accountService.setAccountActive("test-account", false);

      Configuration reloaded = new Gson().fromJson(new FileReader(setup.configFile), Configuration.class);
      assertEquals("the edit must be on disk", false, reloaded.getConfigurations().get(0).isActive());

      assertEquals("a paused account must stay listed, not be removed", 1, setup.accounts.size());
      assertNotSame("the old instance must be replaced, not just mutated", original, setup.accounts.get(0));
    } finally {
      stopReplacedAccount(setup);
    }
  }

  @Test
  public void setAccountActiveIsANoOpWhenAlreadyInThatState() {
    Setup setup = setUp();
    MailAccount original = setup.accounts.get(0);

    setup.accountService.setAccountActive("test-account", true); // already active by default

    assertSame("nothing should be rebuilt when the requested state already holds", original, setup.accounts.get(0));
  }

  @Test(expected = IllegalArgumentException.class)
  public void setAccountActiveRejectsAnUnknownAccountName() {
    Setup setup = setUp();
    setup.accountService.setAccountActive("does-not-exist", false);
  }

  /** Passes the setup's current config fields straight through, only exercising the username/password/rules under test — mirrors the one combined save the settings page now sends (see UpdateAccountApi). */
  private static void updateAccount(Setup setup, String username, String password, List<MailFilterRuleConfiguration> rules) {
    updateAccount(setup, username, password, rules, List.of());
  }

  private static void updateAccount(Setup setup, String username, String password, List<MailFilterRuleConfiguration> rules,
                                     List<LearningShortcutConfiguration> shortcuts) {
    setup.accountService.updateAccount("test-account", setup.config.getHost(), setup.config.getPort(), setup.config.getRunEvery(),
        setup.config.getConnectTimeout(), setup.config.getReadTimeout(),
        setup.config.getClassifierSpamFolderName(), setup.config.getClassifierExcludedFolders(),
        setup.config.getClassifierCorpusRetentionDays(), setup.config.getClassifierCorpusScanBatchSize(),
        setup.config.isDiscoveryTreeDisabled(), username, password, rules, shortcuts);
  }

  private static LearningShortcutConfiguration shortcut(String name, MatcherType matcherType, ActionType actionType, String actionKey) {
    MailFilterRuleMatcherConfiguration matcher = new MailFilterRuleMatcherConfiguration();
    matcher.setType(matcherType);
    MailFilterRuleActionConfiguration action = new MailFilterRuleActionConfiguration();
    action.setType(actionType);
    action.setKey(actionKey);
    LearningShortcutConfiguration shortcut = new LearningShortcutConfiguration();
    shortcut.setName(name);
    shortcut.setMatcher(matcher);
    shortcut.setAction(action);
    return shortcut;
  }

  private static MailFilterRuleConfiguration rule(String fromDomain, String moveToFolder) {
    MailFilterRuleMatcherConfiguration matcher = new MailFilterRuleMatcherConfiguration();
    matcher.setType(MatcherType.FROM_DOMAIN_EQUALS);
    matcher.setKey(fromDomain);
    MailFilterRuleActionConfiguration action = new MailFilterRuleActionConfiguration();
    action.setType(ActionType.MOVE_TO);
    action.setKey(moveToFolder);
    MailFilterRuleConfiguration rule = new MailFilterRuleConfiguration();
    rule.setMatcher(matcher);
    rule.setAction(action);
    return rule;
  }
}
