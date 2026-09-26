package net.pieroxy.mom.rules;

import com.google.gson.Gson;
import net.pieroxy.mom.api.ServiceProvider;
import net.pieroxy.mom.api.SessionStore;
import net.pieroxy.mom.config.credentials.Credential;
import net.pieroxy.mom.config.credentials.CredentialsFile;
import net.pieroxy.mom.config.general.Configuration;
import net.pieroxy.mom.config.general.MailAccountConfiguration;
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
 * {@link ServiceProvider#restartAccount}/{@link ServiceProvider#updateCredentials} are exercised
 * here, in {@code net.pieroxy.mom.rules} rather than {@code net.pieroxy.mom.api} (where
 * {@link ServiceProvider} itself lives), only to reach {@link MailAccount}'s package-private test
 * constructor for the *original* account (so it never dials out for real) — both methods under
 * test are still the public, fully-qualified {@link ServiceProvider} API.
 */
public class ServiceProviderRestartAccountTest {
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

  /** Everything a test needs: the live config object to mutate, and the ServiceProvider under test. */
  private static final class Setup {
    final MailAccountConfiguration config;
    final File configFile;
    final File credentialsFilePath;
    final List<MailAccount> accounts;
    final ServiceProvider serviceProvider;

    Setup(MailAccountConfiguration config, File configFile, File credentialsFilePath,
          List<MailAccount> accounts, ServiceProvider serviceProvider) {
      this.config = config;
      this.configFile = configFile;
      this.credentialsFilePath = credentialsFilePath;
      this.accounts = accounts;
      this.serviceProvider = serviceProvider;
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

    ServiceProvider serviceProvider = new ServiceProvider(null, new SessionStore(), accounts,
        configuration, configFile, credentialsFile, credentialsFilePath, dataFolder);

    return new Setup(config, configFile, credentialsFilePath, accounts, serviceProvider);
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

    MailAccountConfiguration liveConfig = setup.serviceProvider.findAccountConfig("test-account");
    assertSame("findAccountConfig must hand back the live object, not a copy, since callers mutate it in place",
        setup.config, liveConfig);
    liveConfig.setRunEvery(120);

    try {
      setup.serviceProvider.restartAccount("test-account");

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
  public void updateCredentialsChangesUsernameAndPasswordAndRestarts() throws Exception {
    Setup setup = setUp();
    MailAccount original = setup.accounts.get(0);

    try {
      setup.serviceProvider.updateCredentials("test-account", "new-username", "new-password");

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
  public void updateCredentialsLeavesThePasswordUnchangedWhenBlank() throws Exception {
    Setup setup = setUp();

    try {
      setup.serviceProvider.updateCredentials("test-account", "new-username", "");

      CredentialsFile reloaded = new Gson().fromJson(new FileReader(setup.credentialsFilePath), CredentialsFile.class);
      Credential persisted = reloaded.getCredentials().get(CREDENTIALS_KEY);
      assertEquals("username must still change", "new-username", persisted.getUsername());
      assertEquals("a blank password must leave the original one untouched",
          fixture.accountCredential().getPassword(), persisted.getPassword());
    } finally {
      stopReplacedAccount(setup);
    }
  }
}
