package net.pieroxy.mom.services;

import com.google.gson.Gson;
import net.pieroxy.mom.config.credentials.Credential;
import net.pieroxy.mom.config.credentials.CredentialsFile;
import net.pieroxy.mom.config.credentials.PasswordHasher;
import net.pieroxy.mom.config.general.Configuration;
import net.pieroxy.mom.config.general.MailAccountConfiguration;
import net.pieroxy.mom.config.general.SmtpConfiguration;
import net.pieroxy.mom.config.general.SmtpSecurity;
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
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

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

  private static SmtpConfiguration smtp() {
    SmtpConfiguration smtp = new SmtpConfiguration();
    smtp.setHost("smtp.example.com");
    smtp.setPort(587);
    smtp.setSecurity(SmtpSecurity.STARTTLS);
    smtp.setFromAddress("me@example.com");
    return smtp;
  }

  @Test
  public void setSmtpWithOwnCredentialsCreatesADerivedEntryAndPersistsIt() throws Exception {
    Map<String, Credential> credentials = new HashMap<>();
    credentials.put("acct-cred", credential("imap-user"));
    credentials.put("acct-cred-smtp", credential("taken"));
    File credentialsFilePath = new File(tmp.getRoot(), "credentials.json");
    SettingsService settingsService = buildSettingsServiceWithAccounts(credentials, List.of("acct-cred"), null, credentialsFilePath);
    MailAccountConfiguration account = settingsService.getConfiguration().getConfigurations().get(0);

    settingsService.setSmtp(account, smtp(), true, "smtp-user", "smtp-pass");

    assertEquals("acct-cred-smtp-2", account.getSmtp().getCredentials());
    CredentialsFile reloaded = new Gson().fromJson(new FileReader(credentialsFilePath), CredentialsFile.class);
    assertEquals("smtp-user", reloaded.getCredentials().get("acct-cred-smtp-2").getUsername());
    assertEquals("smtp-pass", reloaded.getCredentials().get("acct-cred-smtp-2").getPassword());
    assertEquals("smtp-user", settingsService.resolveSmtpCredential(account).getUsername());
  }

  @Test
  public void setSmtpKeepsTheSavedPasswordWhenBlank() {
    Map<String, Credential> credentials = new HashMap<>();
    credentials.put("acct-cred", credential("imap-user"));
    SettingsService settingsService = buildSettingsServiceWithAccounts(credentials, List.of("acct-cred"), null, new File(tmp.getRoot(), "credentials.json"));
    MailAccountConfiguration account = settingsService.getConfiguration().getConfigurations().get(0);
    settingsService.setSmtp(account, smtp(), true, "smtp-user", "smtp-pass");

    settingsService.setSmtp(account, smtp(), true, "renamed", "");

    assertEquals("renamed", settingsService.resolveSmtpCredential(account).getUsername());
    assertEquals("smtp-pass", settingsService.resolveSmtpCredential(account).getPassword());
  }

  @Test
  public void setSmtpRefusesANewLoginWithoutPasswordAndChangesNothing() {
    Map<String, Credential> credentials = new HashMap<>();
    credentials.put("acct-cred", credential("imap-user"));
    // Unwritable: proves nothing is written.
    File credentialsFilePath = new File(tmp.getRoot(), "nonexistent-dir/credentials.json");
    SettingsService settingsService = buildSettingsServiceWithAccounts(credentials, List.of("acct-cred"), null, credentialsFilePath);
    MailAccountConfiguration account = settingsService.getConfiguration().getConfigurations().get(0);

    try {
      settingsService.setSmtp(account, smtp(), true, "smtp-user", " ");
      fail("should have thrown");
    } catch (IllegalArgumentException expected) {
      // expected
    }
    assertNull(account.getSmtp());
  }

  @Test
  public void setSmtpWithoutOwnCredentialsUsesTheImapLoginAndDropsTheSmtpEntry() throws Exception {
    Map<String, Credential> credentials = new HashMap<>();
    credentials.put("acct-cred", credential("imap-user"));
    File credentialsFilePath = new File(tmp.getRoot(), "credentials.json");
    SettingsService settingsService = buildSettingsServiceWithAccounts(credentials, List.of("acct-cred"), null, credentialsFilePath);
    MailAccountConfiguration account = settingsService.getConfiguration().getConfigurations().get(0);
    settingsService.setSmtp(account, smtp(), true, "smtp-user", "smtp-pass");

    settingsService.setSmtp(account, smtp(), false, null, null);

    assertNull(account.getSmtp().getCredentials());
    assertEquals("imap-user", settingsService.resolveSmtpCredential(account).getUsername());
    CredentialsFile reloaded = new Gson().fromJson(new FileReader(credentialsFilePath), CredentialsFile.class);
    assertEquals(Set.of("acct-cred"), reloaded.getCredentials().keySet());
  }

  @Test
  public void cleanUpDanglingCredentialsKeepsAnSmtpEntry() {
    Map<String, Credential> credentials = new HashMap<>();
    credentials.put("acct-cred", credential("imap-user"));
    credentials.put("smtp-cred", credential("smtp-user"));
    File credentialsFilePath = new File(tmp.getRoot(), "nonexistent-dir/credentials.json");
    SettingsService settingsService = buildSettingsServiceWithAccounts(credentials, List.of("acct-cred"), null, credentialsFilePath);
    SmtpConfiguration smtp = smtp();
    smtp.setCredentials("smtp-cred");
    settingsService.getConfiguration().getConfigurations().get(0).setSmtp(smtp);

    settingsService.cleanUpDanglingCredentials();

    assertEquals("smtp-user", settingsService.resolveCredential("smtp-cred", "test").getUsername());
  }

  private Credential temporaryWebCredential(String password) {
    Credential c = new Credential();
    c.setUsername("admin");
    c.setPasswordHash(PasswordHasher.hash(password));
    c.setTemporary(true);
    return c;
  }

  @Test
  public void changeWebServerPasswordReplacesTheHashAndClearsTemporaryInMemoryAndOnDisk() throws Exception {
    Credential webServerCredential = temporaryWebCredential("temp-password");
    File credentialsFilePath = new File(tmp.getRoot(), "credentials.json");
    SettingsService settingsService = buildSettingsService(webServerCredential, true, credentialsFilePath);
    settingsService.start();
    assertTrue(settingsService.isWebServerPasswordTemporary());

    settingsService.changeWebServerPassword("new-password");

    assertFalse(settingsService.isWebServerPasswordTemporary());
    assertTrue(PasswordHasher.verify("new-password", webServerCredential.getPasswordHash()));

    CredentialsFile reloaded = new Gson().fromJson(new FileReader(credentialsFilePath), CredentialsFile.class);
    Credential reloadedCredential = reloaded.getCredentials().get(WEB_SERVER_CREDENTIALS_KEY);
    assertFalse(reloadedCredential.isTemporary());
    assertTrue(PasswordHasher.verify("new-password", reloadedCredential.getPasswordHash()));
  }

  @Test
  public void changeWebServerPasswordRejectsTheCurrentPassword() {
    Credential webServerCredential = temporaryWebCredential("temp-password");
    File credentialsFilePath = new File(tmp.getRoot(), "nonexistent-dir/credentials.json");
    SettingsService settingsService = buildSettingsService(webServerCredential, true, credentialsFilePath);
    settingsService.start();

    try {
      settingsService.changeWebServerPassword("temp-password");
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      // expected
    }
    assertTrue(settingsService.isWebServerPasswordTemporary());
  }

  @Test
  public void changeWebServerPasswordRejectsABlankPassword() {
    Credential webServerCredential = temporaryWebCredential("temp-password");
    File credentialsFilePath = new File(tmp.getRoot(), "nonexistent-dir/credentials.json");
    SettingsService settingsService = buildSettingsService(webServerCredential, true, credentialsFilePath);
    settingsService.start();

    try {
      settingsService.changeWebServerPassword("  ");
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      // expected
    }
    assertTrue(settingsService.isWebServerPasswordTemporary());
  }

  @Test
  public void startKeepsTheTemporaryFlagWhenMigratingAPlaintextPassword() {
    Credential webServerCredential = new Credential();
    webServerCredential.setUsername("admin");
    webServerCredential.setPassword("plaintext-password");
    webServerCredential.setTemporary(true);
    File credentialsFilePath = new File(tmp.getRoot(), "credentials.json");
    SettingsService settingsService = buildSettingsService(webServerCredential, true, credentialsFilePath);

    settingsService.start();

    assertTrue(settingsService.isWebServerPasswordTemporary());
  }

  @Test
  public void addAccountConfigPicksAFreeCredentialsKeyAndPersistsBothFiles() throws Exception {
    Map<String, Credential> credentials = new HashMap<>();
    credentials.put("web-cred", credential("admin"));
    credentials.put("work", credential("someone-else"));
    File credentialsFilePath = new File(tmp.getRoot(), "credentials.json");
    SettingsService settingsService = buildSettingsServiceWithAccounts(credentials, List.of("work"), "web-cred", credentialsFilePath);
    MailAccountConfiguration account = new MailAccountConfiguration();
    account.setDisplayName("work");

    settingsService.addAccountConfig(account, "me@example.com", "secret", null, false, null, null);

    assertEquals("an existing key must never be overwritten", "work-2", account.getCredentials());
    CredentialsFile reloadedCredentials = new Gson().fromJson(new FileReader(credentialsFilePath), CredentialsFile.class);
    assertEquals("someone-else", reloadedCredentials.getCredentials().get("work").getUsername());
    assertEquals("me@example.com", reloadedCredentials.getCredentials().get("work-2").getUsername());
    Configuration reloadedConfig = new Gson().fromJson(new FileReader(new File(tmp.getRoot(), "config.json")), Configuration.class);
    assertEquals(2, reloadedConfig.getConfigurations().size());
    assertEquals("work-2", reloadedConfig.getConfigurations().get(1).getCredentials());
  }

  @Test
  public void addAccountConfigSavesItsSmtpSettingsWithTheirOwnLogin() throws Exception {
    File credentialsFilePath = new File(tmp.getRoot(), "credentials.json");
    SettingsService settingsService = buildSettingsServiceWithAccounts(new HashMap<>(Map.of("web-cred", credential("admin"))),
        List.of(), "web-cred", credentialsFilePath);
    MailAccountConfiguration account = new MailAccountConfiguration();
    account.setDisplayName("home");
    SmtpConfiguration smtp = new SmtpConfiguration();
    smtp.setHost("smtp.example.com");
    smtp.setFromAddress("me@example.com");
    smtp.setAliases(List.of("@example.org"));

    settingsService.addAccountConfig(account, "me@example.com", "secret", smtp, true, "smtp-user", "smtp-secret");

    CredentialsFile reloadedCredentials = new Gson().fromJson(new FileReader(credentialsFilePath), CredentialsFile.class);
    assertEquals("smtp-user", reloadedCredentials.getCredentials().get("home-smtp").getUsername());
    Configuration reloadedConfig = new Gson().fromJson(new FileReader(new File(tmp.getRoot(), "config.json")), Configuration.class);
    SmtpConfiguration savedSmtp = reloadedConfig.getConfigurations().get(0).getSmtp();
    assertEquals("home-smtp", savedSmtp.getCredentials());
    assertEquals(List.of("@example.org"), savedSmtp.getAliases());
  }

  @Test
  public void addAccountConfigRefusesAnSmtpLoginWithoutPasswordAndChangesNothing() {
    SettingsService settingsService = buildSettingsServiceWithAccounts(new HashMap<>(Map.of("web-cred", credential("admin"))),
        List.of(), "web-cred", new File(tmp.getRoot(), "credentials.json"));
    MailAccountConfiguration account = new MailAccountConfiguration();
    account.setDisplayName("home");
    SmtpConfiguration smtp = new SmtpConfiguration();
    smtp.setFromAddress("me@example.com");

    assertThrows(IllegalArgumentException.class,
        () -> settingsService.addAccountConfig(account, "me@example.com", "secret", smtp, true, "smtp-user", " "));
    assertTrue(settingsService.getConfiguration().getConfigurations() == null
        || settingsService.getConfiguration().getConfigurations().isEmpty());
  }
}
