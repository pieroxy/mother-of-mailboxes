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
 * Usage: {@code java -jar mom-smtp-sink.jar [config.json]} (default: {@code smtp-sink.json}).
 */
public class SmtpSink {
  private final static int CLEANUP_INTERVAL_SECONDS = 30;

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

  public static SinkConfiguration loadConfiguration(File configFile) throws IOException {
    SinkConfiguration config;
    try (Reader reader = new FileReader(configFile)) {
      config = new Gson().fromJson(reader, SinkConfiguration.class);
    }
    if (config == null) throw new IllegalArgumentException(configFile + " is empty.");
    config.validateAndResolve(configFile.getAbsoluteFile().getParentFile());
    return config;
  }

  public static void main(String[] args) throws Exception {
    configureLogging();
    Logger logger = Logger.getLogger(SmtpSink.class.getName());
    File configFile = new File(args.length > 0 ? args[0] : "smtp-sink.json");
    SinkConfiguration config;
    try {
      config = loadConfiguration(configFile);
    } catch (IOException | RuntimeException e) {
      System.err.println("Could not load " + configFile.getAbsolutePath() + ": " + e.getMessage());
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
    DateTimeFormatter timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    Logger root = Logger.getLogger("");
    for (Handler h : root.getHandlers()) root.removeHandler(h);
    ConsoleHandler handler = new ConsoleHandler();
    handler.setFormatter(new Formatter() {
      @Override
      public String format(LogRecord record) {
        String line = timestamp.format(Instant.ofEpochMilli(record.getMillis())) + " " + record.getLevel().getName()
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
