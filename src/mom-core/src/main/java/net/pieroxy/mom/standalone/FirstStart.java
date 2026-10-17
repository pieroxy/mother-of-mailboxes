package net.pieroxy.mom.standalone;

import com.google.gson.GsonBuilder;
import net.pieroxy.mom.config.credentials.Credential;
import net.pieroxy.mom.config.credentials.CredentialsFile;
import net.pieroxy.mom.config.credentials.CredentialsFileStore;
import net.pieroxy.mom.config.credentials.PasswordHasher;
import net.pieroxy.mom.config.general.Configuration;
import net.pieroxy.mom.config.general.WebServerConfiguration;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Creates a minimal config.json and credentials.json when neither exists, so a fresh install
 * only needs {@code java -jar mom-core.jar <dir>}: the web UI then listens on this machine only,
 * with a temporary admin password, and its setup wizard takes over (see
 * {@link Configuration#isSetupInProgress()}).
 */
final class FirstStart {
  final static String ADDRESS = "127.0.0.1";
  final static int FIRST_PORT = 8080;
  final static int LAST_PORT = 8180;
  final static String USERNAME = "admin";
  private final static String CREDENTIALS_KEY = "webui";
  private final static String DEFAULT_DATA_FOLDER = "./data/";
  private final static int DEFAULT_KEEP_LOG_FILES = 14;
  // No look-alikes (0/O, 1/l/I): it's read off a console and typed by hand.
  private final static String PASSWORD_ALPHABET = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private final static int PASSWORD_LENGTH = 16;

  private FirstStart() {
  }

  /**
   * @return the message to show on the console once MOM has started, or null if both files
   *     already existed.
   * @throws IllegalStateException if only one of the two files exists, or no port is free.
   */
  static String createIfMissing(File configDir, File configFile, File credentialsFile) throws IOException {
    boolean hasConfig = configFile.exists();
    boolean hasCredentials = credentialsFile.exists();
    if (hasConfig && hasCredentials) return null;
    if (hasConfig || hasCredentials) {
      File present = hasConfig ? configFile : credentialsFile;
      File missing = hasConfig ? credentialsFile : configFile;
      throw new IllegalStateException("Found " + present.getName() + " but not " + missing.getName() + " in "
          + configDir.getAbsolutePath() + ": restore " + missing.getName() + ", or remove " + present.getName()
          + " to start a fresh setup.");
    }

    int port = findFreePort();
    String password = randomPassword();

    WebServerConfiguration webServer = new WebServerConfiguration();
    webServer.setEnabled(true);
    webServer.setHttpPort(port);
    webServer.setAddress(ADDRESS);
    webServer.setCredentials(CREDENTIALS_KEY);
    Configuration config = new Configuration();
    config.setDataFolder(DEFAULT_DATA_FOLDER);
    config.setKeepLogFiles(DEFAULT_KEEP_LOG_FILES);
    config.setConfigurations(new ArrayList<>());
    config.setReputationLists(new ArrayList<>());
    config.setWebServer(webServer);
    config.setSetupInProgress(true);

    Credential login = new Credential();
    login.setUsername(USERNAME);
    login.setPasswordHash(PasswordHasher.hash(password));
    login.setTemporary(true);
    Map<String, Credential> credentials = new HashMap<>();
    credentials.put(CREDENTIALS_KEY, login);
    CredentialsFile credentialsContent = new CredentialsFile();
    credentialsContent.setCredentials(credentials);

    configDir.mkdirs();
    try (Writer w = new FileWriter(configFile)) {
      new GsonBuilder().setPrettyPrinting().create().toJson(config, w);
    }
    CredentialsFileStore.save(credentialsFile, credentialsContent);
    return banner(configDir, port, password);
  }

  private static int findFreePort() throws IOException {
    InetAddress address = InetAddress.getByName(ADDRESS);
    for (int port = FIRST_PORT; port <= LAST_PORT; port++) {
      try (ServerSocket probe = new ServerSocket()) {
        probe.setReuseAddress(true);
        probe.bind(new InetSocketAddress(address, port));
        return port;
      } catch (IOException taken) {
        // try the next one
      }
    }
    throw new IllegalStateException("No free port between " + FIRST_PORT + " and " + LAST_PORT + " on " + ADDRESS
        + " for the web UI: free one of them and start MOM again.");
  }

  private static String randomPassword() {
    SecureRandom random = new SecureRandom();
    StringBuilder password = new StringBuilder(PASSWORD_LENGTH);
    for (int i = 0; i < PASSWORD_LENGTH; i++) {
      password.append(PASSWORD_ALPHABET.charAt(random.nextInt(PASSWORD_ALPHABET.length())));
    }
    return password.toString();
  }

  private static String banner(File configDir, int port, String password) {
    String line = "=".repeat(78);
    return line + "\n"
        + " First start: created config.json and credentials.json in " + configDir.getAbsoluteFile().toPath().normalize() + "\n"
        + "\n"
        + " Open http://" + ADDRESS + ":" + port + "/ and log in as \"" + USERNAME + "\" with this temporary password:\n"
        + "\n"
        + "     " + password + "\n"
        + "\n"
        + " You'll be asked to change it right away, then to finish the setup.\n"
        + " The web UI only listens on " + ADDRESS + " (this machine). To reach it from another machine,\n"
        + " use an SSH tunnel (ssh -L " + port + ":" + ADDRESS + ":" + port + " <this machine>), or set webServer.address\n"
        + " in config.json and restart MOM.\n"
        + line;
  }
}
