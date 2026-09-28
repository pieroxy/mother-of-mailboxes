package net.pieroxy.mom.detection.reputation;

import com.google.gson.Gson;
import net.pieroxy.mom.config.credentials.Credential;
import net.pieroxy.mom.config.credentials.CredentialsFile;
import net.pieroxy.mom.config.credentials.PasswordHasher;
import net.pieroxy.mom.config.general.Configuration;
import net.pieroxy.mom.config.general.ReputationListConfig;
import net.pieroxy.mom.config.general.WebServerConfiguration;
import net.pieroxy.mom.services.SettingsService;
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
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * {@link SettingsService#updateGeneralSettings} covers the two things GeneralSettingsPage lets
 * you edit — the web server's own login, and the whole reputation lists list — both of which must
 * take effect immediately, with nothing to restart (unlike {@code AccountService#updateAccount}).
 * In {@code net.pieroxy.mom.detection.reputation} rather than alongside {@link SettingsService}
 * itself only to reach {@link ReputationListStore}'s package-private constructor, for seeding a
 * disk cache directly instead of waiting on a real (or fake) network fetch.
 */
public class SettingsServiceUpdateGeneralSettingsTest {
  private static final String WEB_SERVER_CREDENTIALS_KEY = "web";

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private Credential webServerCredential;
  private CredentialsFile credentialsFile;
  private File credentialsFilePath;
  private SettingsService settingsService;

  @Before
  public void setUp() {
    webServerCredential = new Credential();
    webServerCredential.setUsername("admin");
    webServerCredential.setPassword("old-password");

    credentialsFile = new CredentialsFile();
    credentialsFile.setCredentials(Map.of(WEB_SERVER_CREDENTIALS_KEY, webServerCredential));
    credentialsFilePath = new File(tmp.getRoot(), "credentials.json");

    WebServerConfiguration webServerConfig = new WebServerConfiguration();
    webServerConfig.setEnabled(true);
    webServerConfig.setCredentials(WEB_SERVER_CREDENTIALS_KEY);

    Configuration config = new Configuration();
    config.setConfigurations(new ArrayList<>());
    config.setWebServer(webServerConfig);
    config.setDataFolder(tmp.getRoot().getAbsolutePath());

    File configFile = new File(tmp.getRoot(), "config.json");
    settingsService = new SettingsService(config, configFile, credentialsFile, credentialsFilePath, tmp.getRoot().getAbsolutePath());
    settingsService.start();
  }

  @After
  public void tearDown() {
    ReputationRegistryHolder.get().stop();
    ReputationRegistryHolder.set(ReputationRegistry.empty());
  }

  @Test
  public void changesTheWebServerLoginInPlaceAndPersistsIt() throws Exception {
    settingsService.updateGeneralSettings(tmp.getRoot().getAbsolutePath(), 14, true, 8080, "",
        "new-admin", "new-password", List.of());

    assertEquals("new-admin", webServerCredential.getUsername());
    assertEquals("the plaintext password must never be kept once hashed", null, webServerCredential.getPassword());
    assertTrue(PasswordHasher.verify("new-password", webServerCredential.getPasswordHash()));
    // Takes effect immediately: LoginApi checks against this exact object, not a copy.
    assertSame(webServerCredential, settingsService.getWebServerCredential());

    CredentialsFile reloaded = new Gson().fromJson(new FileReader(credentialsFilePath), CredentialsFile.class);
    Credential reloadedCredential = reloaded.getCredentials().get(WEB_SERVER_CREDENTIALS_KEY);
    assertEquals("new-admin", reloadedCredential.getUsername());
    assertEquals("the plaintext password must never be persisted once hashed", null, reloadedCredential.getPassword());
    assertTrue(PasswordHasher.verify("new-password", reloadedCredential.getPasswordHash()));
  }

  @Test
  public void leavesTheWebServerPasswordUnchangedWhenBlank() throws Exception {
    // setUp()'s own start() call already migrated "old-password" to a hash (see
    // SettingsServiceTest) before this test ever runs — a blank password here must leave that
    // hash untouched, not the (long gone) plaintext.
    settingsService.updateGeneralSettings(tmp.getRoot().getAbsolutePath(), 14, true, 8080, "",
        "new-admin", "", List.of());

    assertEquals("new-admin", webServerCredential.getUsername());
    assertTrue("a blank password must leave the original hash untouched",
        PasswordHasher.verify("old-password", webServerCredential.getPasswordHash()));
  }

  @Test
  public void persistsDataFolderKeepLogFilesAndWebServerConnectionSettingsButDoesNotApplyThem() throws Exception {
    File configFile = new File(tmp.getRoot(), "config.json");

    settingsService.updateGeneralSettings("/new/data/folder", 30, false, 9090, "127.0.0.1",
        "admin", "", List.of());

    Configuration reloaded = new Gson().fromJson(new FileReader(configFile), Configuration.class);
    assertEquals("/new/data/folder", reloaded.getDataFolder());
    assertEquals(30, reloaded.getKeepLogFiles());
    assertFalse(reloaded.getWebServer().isEnabled());
    assertEquals(9090, reloaded.getWebServer().getHttpPort());
    assertEquals("127.0.0.1", reloaded.getWebServer().getAddress());
  }

  @Test
  public void hotSwapsTheReputationRegistryWithNoRestart() throws Exception {
    // Seed a disk cache so the fresh registry can load "blocklist" without any network fetch.
    new ReputationListStore(tmp.getRoot().getAbsolutePath()).save("blocklist", "1.2.3.0/24\n");

    ReputationListConfig blocklist = new ReputationListConfig();
    blocklist.setId("blocklist");
    blocklist.setType(ReputationListType.IP_CIDR);
    blocklist.setUrl("file:///unused-in-this-test");
    blocklist.setRefreshHours(24);
    blocklist.setScore(1.0);

    assertFalse("nothing configured yet: the default empty registry must not match",
        ReputationRegistryHolder.get().ipScore("1.2.3.4", Set.of("blocklist")).isPresent());

    settingsService.updateGeneralSettings(tmp.getRoot().getAbsolutePath(), 14, true, 8080, "",
        "admin", "", List.of(blocklist));

    Optional<ReputationMatch> match = ReputationRegistryHolder.get().ipScore("1.2.3.4", Set.of("blocklist"));
    assertTrue("the new list must be live immediately, no restart of anything needed", match.isPresent());
    assertEquals(1.0, match.get().score(), 0.0001);
  }
}
