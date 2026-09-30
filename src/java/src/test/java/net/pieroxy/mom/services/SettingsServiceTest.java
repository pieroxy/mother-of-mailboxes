package net.pieroxy.mom.services;

import com.google.gson.Gson;
import net.pieroxy.mom.config.credentials.Credential;
import net.pieroxy.mom.config.credentials.CredentialsFile;
import net.pieroxy.mom.config.credentials.PasswordHasher;
import net.pieroxy.mom.config.general.Configuration;
import net.pieroxy.mom.config.general.MailAccountConfiguration;
import net.pieroxy.mom.config.general.WebServerConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link SettingsService#start()}: resolves the web server's own login credential (if configured
 * and enabled) and migrates a plaintext password to a hashed one, one time, if it finds one.
 */
public class SettingsServiceTest {
  private static final String WEB_SERVER_CREDENTIALS_KEY = "web";

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private SettingsService buildSettingsService(Credential webServerCredential, boolean webServerEnabled, File credentialsFilePath) {
    CredentialsFile credentialsFile = new CredentialsFile();
    credentialsFile.setCredentials(Map.of(WEB_SERVER_CREDENTIALS_KEY, webServerCredential));

    WebServerConfiguration webServerConfig = new WebServerConfiguration();
    webServerConfig.setEnabled(webServerEnabled);
    webServerConfig.setCredentials(WEB_SERVER_CREDENTIALS_KEY);

    Configuration config = new Configuration();
    config.setConfigurations(new ArrayList<>());
    config.setWebServer(webServerConfig);

    File configFile = new File(tmp.getRoot(), "config.json");
    return new SettingsService(config, configFile, credentialsFile, credentialsFilePath, tmp.getRoot().getAbsolutePath());
  }

  @Test
  public void startHashesAPlaintextPasswordAndClearsItInMemoryAndOnDisk() throws Exception {
    Credential webServerCredential = new Credential();
    webServerCredential.setUsername("admin");
    webServerCredential.setPassword("plaintext-password");
    File credentialsFilePath = new File(tmp.getRoot(), "credentials.json");
    SettingsService settingsService = buildSettingsService(webServerCredential, true, credentialsFilePath);

    settingsService.start();

    assertNull("the plaintext must not survive migration", webServerCredential.getPassword());
    assertTrue(PasswordHasher.verify("plaintext-password", webServerCredential.getPasswordHash()));

    CredentialsFile reloaded = new Gson().fromJson(new FileReader(credentialsFilePath), CredentialsFile.class);
    Credential reloadedCredential = reloaded.getCredentials().get(WEB_SERVER_CREDENTIALS_KEY);
    assertNull("the plaintext must not be persisted either", reloadedCredential.getPassword());
    assertTrue(PasswordHasher.verify("plaintext-password", reloadedCredential.getPasswordHash()));
  }

  @Test
  public void startDoesNothingWhenAlreadyHashed() {
    Credential webServerCredential = new Credential();
    webServerCredential.setUsername("admin");
    webServerCredential.setPasswordHash(PasswordHasher.hash("already-hashed"));
    // Deliberately a path that can't be written to, to prove no write is even attempted.
    File credentialsFilePath = new File(tmp.getRoot(), "nonexistent-dir/credentials.json");
    SettingsService settingsService = buildSettingsService(webServerCredential, true, credentialsFilePath);

    settingsService.start();

    assertTrue(PasswordHasher.verify("already-hashed", webServerCredential.getPasswordHash()));
  }

  @Test
  public void startDoesNothingWhenThereIsNoPasswordAtAll() {
    Credential webServerCredential = new Credential();
    webServerCredential.setUsername("admin");
    File credentialsFilePath = new File(tmp.getRoot(), "nonexistent-dir/credentials.json");
    SettingsService settingsService = buildSettingsService(webServerCredential, true, credentialsFilePath);

    settingsService.start();

    assertNull(webServerCredential.getPassword());
    assertNull(webServerCredential.getPasswordHash());
  }

  @Test
  public void startLeavesTheCredentialUnresolvedWhenTheWebServerIsDisabled() {
    Credential webServerCredential = new Credential();
    webServerCredential.setUsername("admin");
    webServerCredential.setPassword("plaintext-password");
    File credentialsFilePath = new File(tmp.getRoot(), "nonexistent-dir/credentials.json");
    SettingsService settingsService = buildSettingsService(webServerCredential, false, credentialsFilePath);

    settingsService.start();

    assertNull("a disabled web server must never be resolved or migrated", settingsService.getWebServerCredential());
    assertTrue("the original credential object must be left untouched", "plaintext-password".equals(webServerCredential.getPassword()));
  }

  private Credential credential(String username) {
    Credential c = new Credential();
    c.setUsername(username);
    c.setPasswordHash(PasswordHasher.hash("whatever"));
    return c;
  }

  private SettingsService buildSettingsServiceWithAccounts(Map<String, Credential> credentialsMap, List<String> accountCredentialKeys,
                                                            String webServerCredentialsKey, File credentialsFilePath) {
    CredentialsFile credentialsFile = new CredentialsFile();
    credentialsFile.setCredentials(credentialsMap);

    List<MailAccountConfiguration> accounts = new ArrayList<>();
    for (String key : accountCredentialKeys) {
      MailAccountConfiguration account = new MailAccountConfiguration();
      account.setDisplayName("account-" + key);
      account.setCredentials(key);
      accounts.add(account);
    }

    Configuration config = new Configuration();
    config.setConfigurations(accounts);
    if (webServerCredentialsKey != null) {
      WebServerConfiguration webServerConfig = new WebServerConfiguration();
      webServerConfig.setCredentials(webServerCredentialsKey);
      config.setWebServer(webServerConfig);
    }

    File configFile = new File(tmp.getRoot(), "config.json");
    return new SettingsService(config, configFile, credentialsFile, credentialsFilePath, tmp.getRoot().getAbsolutePath());
  }

  @Test
  public void cleanUpDanglingCredentialsRemovesAnEntryNothingReferencesAnymore() throws Exception {
    Map<String, Credential> credentials = new HashMap<>();
    credentials.put("acct-cred", credential("account-user"));
    credentials.put("web-cred", credential("admin"));
    credentials.put("orphan-cred", credential("nobody"));
    File credentialsFilePath = new File(tmp.getRoot(), "credentials.json");
    SettingsService settingsService = buildSettingsServiceWithAccounts(credentials, List.of("acct-cred"), "web-cred", credentialsFilePath);

    settingsService.cleanUpDanglingCredentials();

    CredentialsFile reloaded = new Gson().fromJson(new FileReader(credentialsFilePath), CredentialsFile.class);
    assertTrue("a still-referenced account credential must survive", reloaded.getCredentials().containsKey("acct-cred"));
    assertTrue("the web server credential must survive", reloaded.getCredentials().containsKey("web-cred"));
    assertFalse("an orphaned credential must be removed", reloaded.getCredentials().containsKey("orphan-cred"));
  }

  @Test
  public void cleanUpDanglingCredentialsKeepsTheWebServerEntryEvenWhenDisabled() {
    Map<String, Credential> credentials = new HashMap<>();
    credentials.put("web-cred", credential("admin"));
    // Deliberately a path that can't be written to: nothing is dangling here, so proving the
    // credential survives means proving no write was even attempted, same as the "never writes"
    // test below — reloading from disk wouldn't work, since the file is never created at all.
    File credentialsFilePath = new File(tmp.getRoot(), "nonexistent-dir/credentials.json");
    // webServerConfig defaults to enabled=false (see buildSettingsServiceWithAccounts) — the
    // mapping must still count as referenced, since disabling doesn't detach it.
    SettingsService settingsService = buildSettingsServiceWithAccounts(credentials, List.of(), "web-cred", credentialsFilePath);

    settingsService.cleanUpDanglingCredentials();

    assertTrue("a disabled web server's credential is still referenced, not dangling",
        PasswordHasher.verify("whatever", settingsService.resolveCredential("web-cred", "test").getPasswordHash()));
  }

  @Test
  public void cleanUpDanglingCredentialsNeverWritesWhenNothingIsDangling() {
    Map<String, Credential> credentials = new HashMap<>();
    credentials.put("acct-cred", credential("account-user"));
    // Deliberately a path that can't be written to, to prove no write is even attempted.
    File credentialsFilePath = new File(tmp.getRoot(), "nonexistent-dir/credentials.json");
    SettingsService settingsService = buildSettingsServiceWithAccounts(credentials, List.of("acct-cred"), null, credentialsFilePath);

    settingsService.cleanUpDanglingCredentials();
  }
}
