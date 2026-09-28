package net.pieroxy.mom.standalone;

import com.google.gson.Gson;
import net.pieroxy.mom.services.AccountService;
import net.pieroxy.mom.services.ServiceProvider;
import net.pieroxy.mom.services.SessionService;
import net.pieroxy.mom.services.SettingsService;
import net.pieroxy.mom.services.WebServerService;
import net.pieroxy.mom.config.general.Configuration;
import net.pieroxy.mom.config.credentials.CredentialsFile;
import net.pieroxy.mom.utils.logging.LoggingBootstrap;
import net.pieroxy.mom.detection.reputation.ReputationRegistry;
import net.pieroxy.mom.detection.reputation.ReputationRegistryHolder;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Runner {
  private final static Logger LOGGER = Logger.getLogger(Runner.class.getName());
  private final static String GIT_REV;
  private final static String MVN_VER;
  private static Configuration config;
  private static String logFile;
  private static ServiceProvider serviceProvider;
  private static ReputationRegistry reputationRegistry;

  static {
    GIT_REV = readResourceFileAsString("GIT_REV");
    MVN_VER = readResourceFileAsString("MVN_VER");
  }

  private static String readResourceFileAsString(String filename) {
    try {
      InputStream is = Runner.class.getClassLoader().getResourceAsStream(filename);
      return new BufferedReader(new InputStreamReader(is)).lines().findFirst().get();
    } catch (Exception e) {
      LOGGER.log(Level.SEVERE, "Could not read " + filename, e);
      return filename + "_" + Math.random();
    }
  }

  public static void main(String[] args) throws Exception {
    Gson gson = new Gson();
    File configFile = new File(args[0], "config.json");
    Runner.config = gson.fromJson(new FileReader(configFile), Configuration.class);
    File credentialsFilePath = new File(args[0], "credentials.json");
    CredentialsFile credentialsFile = gson.fromJson(new FileReader(credentialsFilePath), CredentialsFile.class);
    logFile = new File(config.getDataFolder(), "logs/log.txt").getAbsolutePath();
    LoggingBootstrap.configure(logFile, config.getKeepLogFiles());

    reputationRegistry = new ReputationRegistry(config.getReputationLists(), config.getDataFolder());
    reputationRegistry.start();
    ReputationRegistryHolder.set(reputationRegistry);

    SettingsService settingsService = new SettingsService(config, configFile, credentialsFile, credentialsFilePath, config.getDataFolder());
    AccountService accountService = new AccountService(settingsService);
    SessionService sessionService = new SessionService();
    WebServerService webServerService = new WebServerService(config.getWebServer(), config.getDataFolder());
    serviceProvider = new ServiceProvider(settingsService, accountService, sessionService, webServerService);
    serviceProvider.init();

    Runtime.getRuntime().addShutdownHook(new Thread(Runner::shutdown, "shutdown-hook"));
    LOGGER.info("Started MOM (Mother Of Mailboxes) version " + MVN_VER + " rev " + GIT_REV);
  }

  private static void shutdown() {
    logDirectly("Shutting down...");
    // An IMAP cycle already in progress (blocking socket I/O) won't be interrupted on the spot;
    // this only prevents a new cycle from starting and lets an in-progress cycle finish within
    // AccountService's own timeout (see MailAccount#requestStop for the IMAP IDLE case). Stops
    // every service, Tomcat included, in the reverse of whatever order they actually started in.
    if (serviceProvider != null) {
      serviceProvider.destroy();
    }
    // Not the "reputationRegistry" field directly: SettingsService#updateGeneralSettings can have
    // hot-swapped it for a fresh instance since startup (see ReputationRegistryHolder) — stopping
    // the original would leave that current one's own refresh scheduler thread running.
    ReputationRegistry currentReputationRegistry = ReputationRegistryHolder.get();
    if (currentReputationRegistry != null) {
      currentReputationRegistry.stop();
    }
    LoggingBootstrap.shutdown();
    logDirectly("Shutdown complete.");
  }

  /**
   * java.util.logging installs its own shutdown hook (LogManager) that resets the handlers; the
   * execution order between concurrent hooks isn't guaranteed, so a call to LOGGER here could
   * silently disappear depending on which of the two hooks runs first. So we write directly to
   * stderr and to the log file, the only reliable channels at this stage of shutdown.
   */
  private static void logDirectly(String message) {
    String line = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " " + message;
    System.err.println(line);
    try (FileWriter writer = new FileWriter(logFile, true)) {
      writer.write(line + System.lineSeparator());
    } catch (IOException ignored) {
      // best effort: nothing more reliable to do at this stage of shutdown.
    }
  }

  private static boolean has(String[] args, String lookFor) {
    for (String s : args) if (s.equals(lookFor)) return true;
    return false;
  }
}
