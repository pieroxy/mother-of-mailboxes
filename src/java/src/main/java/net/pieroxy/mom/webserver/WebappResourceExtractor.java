package net.pieroxy.mom.webserver;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Extracts the webapp's static files (see {@code src/webapp}, built and embedded under the
 * "webapp/" classpath resource by the Maven build) to a real directory on disk: Tomcat's static
 * file serving needs an actual docBase, not classpath resources inside a jar. Runs fresh on every
 * startup — the target directory is wiped first — so an upgraded jar's UI is never served stale.
 */
public class WebappResourceExtractor {
  private final static Logger LOGGER = Logger.getLogger(WebappResourceExtractor.class.getName());
  private final static String RESOURCE_ROOT = "webapp/";

  public static void extract(File targetDir) throws IOException {
    deleteRecursively(targetDir);
    Files.createDirectories(targetDir.toPath());

    URL location = WebappResourceExtractor.class.getProtectionDomain().getCodeSource().getLocation();
    File locationFile;
    try {
      locationFile = new File(location.toURI());
    } catch (URISyntaxException e) {
      throw new IOException("Could not resolve code source location " + location, e);
    }

    // Packaged as a jar (production) vs. run from exploded classes (tests, IDE): the resources
    // live in the jar's "webapp/" entries in the first case, in a "webapp/" subdirectory of the
    // classes directory in the second.
    if (locationFile.isFile()) {
      extractFromJar(locationFile, targetDir);
    } else {
      extractFromDirectory(new File(locationFile, RESOURCE_ROOT), targetDir);
    }
  }

  private static void extractFromJar(File jarFile, File targetDir) throws IOException {
    try (JarFile jar = new JarFile(jarFile)) {
      Enumeration<JarEntry> entries = jar.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        if (entry.isDirectory() || !entry.getName().startsWith(RESOURCE_ROOT)) continue;
        File out = new File(targetDir, entry.getName().substring(RESOURCE_ROOT.length()));
        Files.createDirectories(out.getParentFile().toPath());
        try (InputStream in = jar.getInputStream(entry)) {
          Files.copy(in, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
  }

  private static void extractFromDirectory(File sourceDir, File targetDir) throws IOException {
    if (!sourceDir.isDirectory()) {
      LOGGER.log(Level.WARNING, "No webapp resources found at " + sourceDir.getAbsolutePath()
          + " — did the frontend build run? (see src/webapp)");
      return;
    }
    try (var paths = Files.walk(sourceDir.toPath())) {
      for (Path path : (Iterable<Path>) paths::iterator) {
        Path relative = sourceDir.toPath().relativize(path);
        Path target = targetDir.toPath().resolve(relative.toString());
        if (Files.isDirectory(path)) {
          Files.createDirectories(target);
        } else {
          Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
  }

  private static void deleteRecursively(File dir) throws IOException {
    if (!dir.exists()) return;
    try (var paths = Files.walk(dir.toPath())) {
      for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) {
        Files.delete(path);
      }
    }
  }
}
