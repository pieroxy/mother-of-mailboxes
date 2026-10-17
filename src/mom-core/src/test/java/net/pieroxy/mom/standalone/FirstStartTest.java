package net.pieroxy.mom.standalone;

import com.google.gson.Gson;
import net.pieroxy.mom.config.credentials.Credential;
import net.pieroxy.mom.config.credentials.CredentialsFile;
import net.pieroxy.mom.config.credentials.PasswordHasher;
import net.pieroxy.mom.config.general.Configuration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FirstStartTest {
  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  @Test
  public void createsBothFilesWithATemporaryLoginAndSetupInProgress() throws Exception {
    File configDir = new File(tmp.getRoot(), "conf");
    File configFile = new File(configDir, "config.json");
    File credentialsFile = new File(configDir, "credentials.json");

    String message = FirstStart.createIfMissing(configDir, configFile, credentialsFile);

    Configuration config = new Gson().fromJson(new FileReader(configFile), Configuration.class);
    assertTrue(config.isSetupInProgress());
    assertEquals("./data/", config.getDataFolder());
    assertTrue(config.getConfigurations().isEmpty());
    assertTrue(config.getWebServer().isEnabled());
    assertEquals(FirstStart.ADDRESS, config.getWebServer().getAddress());
    int port = config.getWebServer().getHttpPort();
    assertTrue("port " + port, port >= FirstStart.FIRST_PORT && port <= FirstStart.LAST_PORT);

    Matcher password = Pattern.compile("\\n {5}(\\S+)\\n").matcher(message);
    assertTrue(message, password.find());
    assertTrue(message.contains("http://" + FirstStart.ADDRESS + ":" + port + "/"));
    Credential login = new Gson().fromJson(new FileReader(credentialsFile), CredentialsFile.class)
        .getCredentials().get(config.getWebServer().getCredentials());
    assertEquals(FirstStart.USERNAME, login.getUsername());
    assertTrue(login.isTemporary());
    assertNull("only the hash may be stored", login.getPassword());
    assertTrue(PasswordHasher.verify(password.group(1), login.getPasswordHash()));
  }

  @Test
  public void doesNothingWhenBothFilesExist() throws Exception {
    File configFile = tmp.newFile("config.json");
    File credentialsFile = tmp.newFile("credentials.json");

    assertNull(FirstStart.createIfMissing(tmp.getRoot(), configFile, credentialsFile));
    assertEquals(0, Files.size(configFile.toPath()));
  }

  @Test
  public void refusesWhenOnlyOneFileExists() throws Exception {
    File configFile = tmp.newFile("config.json");
    File credentialsFile = new File(tmp.getRoot(), "credentials.json");
    try {
      FirstStart.createIfMissing(tmp.getRoot(), configFile, credentialsFile);
      fail("should have thrown");
    } catch (IllegalStateException expected) {
      assertTrue(expected.getMessage(), expected.getMessage().contains("credentials.json"));
    }
    assertFalse(credentialsFile.exists());
  }
}
