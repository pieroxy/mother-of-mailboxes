package net.pieroxy.imf.standalone;

import com.google.gson.Gson;
import net.pieroxy.imf.api.ServiceProvider;
import net.pieroxy.imf.config.general.Configuration;
import net.pieroxy.imf.config.credentials.Credential;
import net.pieroxy.imf.config.credentials.CredentialsFile;
import net.pieroxy.imf.utils.CredentialsResolver;
import net.pieroxy.imf.utils.logging.LoggingBootstrap;
import net.pieroxy.imf.detection.reputation.ReputationRegistry;
import net.pieroxy.imf.detection.reputation.ReputationRegistryHolder;
import net.pieroxy.imf.rules.MailAccount;
import net.pieroxy.imf.webserver.WebServerRunner;
import org.apache.catalina.startup.Tomcat;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Runner {
  private final static Logger LOGGER = Logger.getLogger(Runner.class.getName());
  private final static long SHUTDOWN_JOIN_TIMEOUT_MS = 5000;
  private final static String GIT_REV;
  private final static String MVN_VER;
  private static Configuration config;
  private static String logFile;
  private static final List<MailAccount> accounts = new ArrayList<>();
  private static ReputationRegistry reputationRegistry;
  private static Tomcat webServer;

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
    Runner.config = gson.fromJson(new FileReader(new File(args[0], "config.json")), Configuration.class);
    CredentialsFile credentialsFile = gson.fromJson(new FileReader(new File(args[0], "credentials.json")), CredentialsFile.class);
    logFile = new File(config.getDataFolder(), "logs/log.txt").getAbsolutePath();
    LoggingBootstrap.configure(logFile, config.getKeepLogFiles());

    reputationRegistry = new ReputationRegistry(config.getReputationLists(), config.getDataFolder());
    reputationRegistry.start();
    ReputationRegistryHolder.set(reputationRegistry);

    config.getConfigurations().forEach(conf -> {
      Credential credential = CredentialsResolver.resolve(conf.getCredentials(), credentialsFile, "mail account \"" + conf.getDisplayName() + "\"");
      MailAccount account = new MailAccount(conf, credential, config.getDataFolder());
      accounts.add(account);
      account.start();
    });

    if (config.getWebServer() != null && config.getWebServer().isEnabled()) {
      Credential webServerCredential = CredentialsResolver.resolve(config.getWebServer().getCredentials(), credentialsFile, "webServer");
      ServiceProvider serviceProvider = new ServiceProvider(webServerCredential);
      webServer = WebServerRunner.start(config.getWebServer(), config.getDataFolder(), serviceProvider);
    }

    Runtime.getRuntime().addShutdownHook(new Thread(Runner::shutdown, "shutdown-hook"));
    LOGGER.info("Started IMAP-MAIL-FILTER version " + MVN_VER + " rev " + GIT_REV);
  }

  private static void shutdown() {
    logDirectly("Shutting down, interrupting " + accounts.size() + " account thread(s)...");
    // An IMAP cycle already in progress (blocking socket I/O) won't be interrupted on the spot;
    // this only prevents a new cycle from starting and lets an in-progress cycle finish within
    // the timeout below (see MailAccount#requestStop for the IMAP IDLE case).
    accounts.forEach(MailAccount::requestStop);
    // One shared deadline, not SHUTDOWN_JOIN_TIMEOUT_MS per account: accounts die concurrently in
    // the background regardless of which one we're currently join()ing, so budgeting per-account
    // would let a slow one after a fast one add its own full timeout on top for nothing.
    long deadline = System.currentTimeMillis() + SHUTDOWN_JOIN_TIMEOUT_MS;
    for (MailAccount account : accounts) {
      try {
        long remainingMs = deadline - System.currentTimeMillis();
        if (remainingMs > 0) account.join(remainingMs);
      } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
      }
    }
    if (webServer != null) {
      WebServerRunner.stop(webServer);
    }
    if (reputationRegistry != null) {
      reputationRegistry.stop();
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
