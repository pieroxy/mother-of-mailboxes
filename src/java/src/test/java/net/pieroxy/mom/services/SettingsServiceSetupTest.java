package net.pieroxy.mom.services;

import com.google.gson.Gson;
import net.pieroxy.mom.config.credentials.CredentialsFile;
import net.pieroxy.mom.config.general.Configuration;
import net.pieroxy.mom.config.general.ReputationListConfig;
import net.pieroxy.mom.detection.reputation.ReputationListType;
import net.pieroxy.mom.detection.reputation.ReputationRegistry;
import net.pieroxy.mom.detection.reputation.ReputationRegistryHolder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** {@link SettingsService#saveSetupStorage} and {@link SettingsService#completeSetup}. */
public class SettingsServiceSetupTest {
  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private File configFile;
  private SettingsService settingsService;

  @Before
  public void setUp() {
    Configuration config = new Configuration();
    config.setConfigurations(new ArrayList<>());
    config.setDataFolder("./data/");
    config.setSetupInProgress(true);
    configFile = new File(tmp.getRoot(), "config.json");
    String dataFolder = config.resolveDataFolder(tmp.getRoot());
    settingsService = new SettingsService(config, configFile, new CredentialsFile(), new File(tmp.getRoot(), "credentials.json"), dataFolder);
  }

  @After
  public void tearDown() {
    ReputationRegistryHolder.get().stop();
    ReputationRegistryHolder.set(ReputationRegistry.empty());
  }

  @Test
  public void movingTheDataFolderAppliesRightAwayAndSavesTheRawValue() throws Exception {
    String previous = settingsService.saveSetupStorage("elsewhere", 30);

    assertEquals(new File(tmp.getRoot(), "data").getAbsolutePath(), previous);
    assertEquals(new File(tmp.getRoot(), "elsewhere").getAbsolutePath(), settingsService.getDataFolder());
    assertTrue(new File(tmp.getRoot(), "elsewhere").isDirectory());
    Configuration saved = reload();
    assertEquals("elsewhere", saved.getDataFolder());
    assertEquals(30, saved.getKeepLogFiles());
  }

  @Test
  public void keepingTheSameDataFolderReportsNoMove() {
    assertNull(settingsService.saveSetupStorage("data", 14));
  }

  @Test
  public void completingAddsTheListsKeepsExistingOnesAndEndsTheSetup() throws Exception {
    settingsService.getConfiguration().setReputationLists(new ArrayList<>(List.of(list("mine"))));

    settingsService.completeSetup(List.of(list("mine"), list("recommended")));

    Configuration saved = reload();
    assertFalse(saved.isSetupInProgress());
    assertEquals(2, saved.getReputationLists().size());
    assertEquals("recommended", saved.getReputationLists().get(1).getId());
  }

  @Test(expected = IllegalStateException.class)
  public void setupStepsAreRefusedOnceTheSetupIsComplete() {
    settingsService.completeSetup(List.of());
    settingsService.saveSetupStorage("data", 14);
  }

  private Configuration reload() throws Exception {
    try (FileReader reader = new FileReader(configFile)) {
      return new Gson().fromJson(reader, Configuration.class);
    }
  }

  private static ReputationListConfig list(String id) {
    ReputationListConfig list = new ReputationListConfig();
    list.setId(id);
    list.setType(ReputationListType.DOMAIN);
    list.setUrl("file:///unused-in-this-test");
    list.setRefreshHours(24);
    list.setScore(1.0);
    return list;
  }
}
