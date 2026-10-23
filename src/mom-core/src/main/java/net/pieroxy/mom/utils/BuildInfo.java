package net.pieroxy.mom.utils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;

/** The version and git revision the build wrote into the jar's resources (see the pom's export-version-info). */
public final class BuildInfo {
  private final static Logger LOGGER = Logger.getLogger(BuildInfo.class.getName());
  public final static String VERSION = readResourceFileAsString("MVN_VER");
  public final static String REVISION = readResourceFileAsString("GIT_REV");

  private BuildInfo() {
  }

  private static String readResourceFileAsString(String filename) {
    try {
      InputStream is = BuildInfo.class.getClassLoader().getResourceAsStream(filename);
      return new BufferedReader(new InputStreamReader(is)).lines().findFirst().get();
    } catch (Exception e) {
      LOGGER.log(Level.SEVERE, "Could not read " + filename, e);
      return filename + "_" + Math.random();
    }
  }
}
