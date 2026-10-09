package net.pieroxy.mom.config.general;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.assertEquals;

public class ConfigurationResolveDataFolderTest {
  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private static String resolve(String dataFolder, File configDir) {
    Configuration config = new Configuration();
    config.setDataFolder(dataFolder);
    return config.resolveDataFolder(configDir);
  }

  @Test
  public void anAbsolutePathIsKeptAsIs() {
    String absolute = new File(tmp.getRoot(), "elsewhere").getAbsolutePath();

    assertEquals(absolute, resolve(absolute, new File("/some/config/dir")));
  }

  @Test
  public void aRelativePathIsResolvedAgainstTheConfigDirectoryNotTheWorkingDirectory() {
    File configDir = tmp.getRoot();

    assertEquals(new File(configDir, "data").getAbsolutePath(), resolve("data", configDir));
    assertEquals(configDir.getAbsolutePath(), resolve(".", configDir));
    assertEquals(new File(configDir.getParentFile(), "shared").getAbsolutePath(), resolve("../shared", configDir));
  }

  @Test
  public void aRelativeConfigDirectoryIsMadeAbsolute() {
    assertEquals(new File("data").getAbsolutePath(), resolve("data", new File(".")));
  }

  @Test(expected = IllegalStateException.class)
  public void aMissingDataFolderIsRefused() {
    resolve(null, tmp.getRoot());
  }
}
