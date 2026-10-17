package net.pieroxy.mom.sink;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * The MOM SMTP sink: receives the test messages MOM instances send to check their outgoing mail
 * setup, and hands each one back over HTTP for a few minutes. See {@link SmtpSession} for what's
 * accepted, {@link HttpApi} for how it's fetched.
 * <p>
 * Usage: {@code java -jar smtp-sink-core.jar <configDir>}, reading {@code <configDir>/config.json} —
 * same convention as MOM itself.
 */
public class SmtpSink {
  private final static int CLEANUP_INTERVAL_SECONDS = 30;
  private final static DateTimeFormatter LOG_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

  private final SmtpServer smtp;
  private final HttpApi http;
  private final MessageStore store;
  private final int cleanupIntervalSeconds;
  private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "cleanup"));

  public SmtpSink(SinkConfiguration config) throws IOException, GeneralSecurityException {
    store = new MessageStore(config.getStorageDir(), config.getRetentionSeconds());
    TlsContextProvider tls = config.getTls() == null ? null : new TlsContextProvider(config.getTls());
    smtp = new SmtpServer(config, tls, store);
    http = new HttpApi(config, store);
    cleanupIntervalSeconds = Math.min(CLEANUP_INTERVAL_SECONDS, config.getRetentionSeconds());
  }

  public void start() {
    cleaner.scheduleWithFixedDelay(store::cleanup, 0, cleanupIntervalSeconds, TimeUnit.SECONDS);
    smtp.start();
    http.start();
  }

  public void stop() {
    // Straight to stderr: java.util.logging's own shutdown hook may already have closed its
    // handlers by the time this one runs (same reason as MOM's Runner#logDirectly).
    System.err.println(LOG_TIMESTAMP.format(Instant.now()) + " INFO MOM SMTP sink stopping");
    smtp.stop();
    http.stop();
    cleaner.shutdownNow();
  }

  public int getSmtpPort() {
    return smtp.getPort();
  }

  public int getHttpPort() {
    return http.getPort();
  }

  /** Reads {@code <configDir>/config.json}; relative paths in it are resolved against {@code configDir}. */
  public static SinkConfiguration loadConfiguration(File configDir) throws IOException {
    File configFile = new File(configDir, "config.json");
    if (!configFile.isFile()) throw new IllegalArgumentException("missing " + configFile.getAbsolutePath());
    SinkConfiguration config;
    try (Reader reader = new FileReader(configFile)) {
      config = new Gson().fromJson(reader, SinkConfiguration.class);
    }
    if (config == null) throw new IllegalArgumentException(configFile + " is empty.");
    config.validateAndResolve(configDir.getAbsoluteFile());
    return config;
  }

  public static void main(String[] args) throws Exception {
    configureLogging();
    Logger logger = Logger.getLogger(SmtpSink.class.getName());
    if (args.length < 1) {
      System.err.println("Usage: java -jar smtp-sink-core.jar <configDir>");
      System.exit(1);
      return;
    }
    File configDir = new File(args[0]);
    SinkConfiguration config;
    try {
      config = loadConfiguration(configDir);
    } catch (IOException | RuntimeException e) {
      System.err.println("Could not load the configuration: " + e.getMessage());
      System.exit(1);
      return;
    }
    SmtpSink sink;
    try {
      sink = new SmtpSink(config);
    } catch (IOException | GeneralSecurityException e) {
      System.err.println("Could not start: " + e.getMessage());
      System.exit(1);
      return;
    }
    Runtime.getRuntime().addShutdownHook(new Thread(sink::stop, "shutdown-hook"));
    sink.start();
    logger.info("MOM SMTP sink started as " + config.getHostname() + ", storing in " + config.getStorageDir()
        + " for " + config.getRetentionSeconds() + "s");
  }

  /** One line per entry on stderr — meant to be collected by systemd/docker, no log files of its own. */
  private static void configureLogging() {
    Logger root = Logger.getLogger("");
    for (Handler h : root.getHandlers()) root.removeHandler(h);
    ConsoleHandler handler = new ConsoleHandler();
    handler.setFormatter(new Formatter() {
      @Override
      public String format(LogRecord record) {
        String line = LOG_TIMESTAMP.format(Instant.ofEpochMilli(record.getMillis())) + " " + record.getLevel().getName()
            + " " + formatMessage(record) + "\n";
        if (record.getThrown() != null) {
          java.io.StringWriter sw = new java.io.StringWriter();
          record.getThrown().printStackTrace(new java.io.PrintWriter(sw));
          line += sw;
        }
        return line;
      }
    });
    root.addHandler(handler);
  }
}
