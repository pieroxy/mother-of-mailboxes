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
 * {@link ServiceProvider#restartAccount} is exercised here, in {@code net.pieroxy.mom.rules}
 * rather than {@code net.pieroxy.mom.api} (where {@link ServiceProvider} itself lives), only to
 * reach {@link MailAccount}'s package-private test constructor for the *original* account (so it
 * never dials out for real) — {@code restartAccount} is still the public, fully-qualified API
 * under test.
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

  @Test
  public void restartAccountPersistsTheEditAndReplacesTheRunningInstance() throws Exception {
    MailAccountConfiguration config = fixture.accountConfig("test-account");
    config.setCredentials(CREDENTIALS_KEY);
    Credential credential = fixture.accountCredential();

    CredentialsFile credentialsFile = new CredentialsFile();
    credentialsFile.setCredentials(Map.of(CREDENTIALS_KEY, credential));

    Configuration configuration = new Configuration();
    configuration.setConfigurations(new ArrayList<>(List.of(config)));
    String dataFolder = tmp.getRoot().getAbsolutePath();
    File configFile = new File(tmp.getRoot(), "config.json");

    // The test-injectable constructor: this original instance must never actually dial out —
    // only the account restartAccount() builds afterward (via the real 3-arg constructor) does,
    // and that one fails fast against GreenMail's plain IMAP port (production always asks for
    // "imaps") rather than hanging, so it's safe to just stop it again at the end of the test.
    MailAccount original = new MailAccount(config, credential, dataFolder, (c, cred) -> fixture.connectAsImapMailbox());
    List<MailAccount> accounts = new CopyOnWriteArrayList<>(List.of(original));

    ServiceProvider serviceProvider = new ServiceProvider(null, new SessionStore(), accounts,
        configuration, configFile, credentialsFile, dataFolder);

    MailAccountConfiguration liveConfig = serviceProvider.findAccountConfig("test-account");
    assertSame("findAccountConfig must hand back the live object, not a copy, since callers mutate it in place",
        config, liveConfig);
    liveConfig.setRunEvery(120);

    MailAccount replaced;
    try {
      serviceProvider.restartAccount("test-account");

      Configuration reloaded = new Gson().fromJson(new FileReader(configFile), Configuration.class);
      assertEquals("the edit must be on disk", 120, reloaded.getConfigurations().get(0).getRunEvery());

      replaced = accounts.get(0);
      assertNotSame("the old instance must be replaced, not just mutated", original, replaced);
      assertEquals("the new instance must be built from the updated config", 120, replaced.getConfig().getRunEvery());
    } finally {
      // The instance restartAccount() started for real: shut it down so it doesn't keep retrying
      // a doomed IMAPS handshake against GreenMail in the background after the test ends.
      MailAccount toStop = accounts.get(0);
      toStop.requestStop();
      toStop.join(2000);
    }
  }
}
