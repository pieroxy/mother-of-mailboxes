package net.pieroxy.mom.utils.logging;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Configures java.util.logging at startup. First lifts the ceiling of the handlers already in
 * place (by default, the JDK's ConsoleHandler filters out anything below INFO): without this, a
 * node whose logLevel is set to DEBUG would never show up, whatever its own level, because the
 * handler would already have discarded it upstream. Then adds a log file receiving everything
 * that passes through, with daily rotation (lz4 compression + pruning) for as long as the
 * program runs. The first rotation is timed to midnight (local time) so that every archived file
 * corresponds to a full day, regardless of what time the program started.
 */
public final class LoggingBootstrap {
  private final static Logger LOGGER = Logger.getLogger(LoggingBootstrap.class.getName());
  private final static long ROTATION_PERIOD_HOURS = 24;

  private static String logFile;
  private static int keepLogFiles;
  private static FileHandler fileHandler;
  private static ScheduledExecutorService rotationScheduler;

  private LoggingBootstrap() {}

  public static void configure(String logFile, int keepLogFiles) {
    for (Handler handler : Logger.getLogger("").getHandlers()) {
      handler.setLevel(Level.ALL);
      handler.setFormatter(new OneLineLogFormatter());
    }

    LoggingBootstrap.logFile = logFile;
    LoggingBootstrap.keepLogFiles = keepLogFiles;
    openFileHandler();

    if (keepLogFiles > 0) {
      rotationScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "log-rotation");
        t.setDaemon(true);
        return t;
      });
      rotationScheduler.scheduleAtFixedRate(
          LoggingBootstrap::rotate,
          millisUntilNextMidnight(),
          TimeUnit.HOURS.toMillis(ROTATION_PERIOD_HOURS),
          TimeUnit.MILLISECONDS);
    }
  }

  public static void shutdown() {
    if (rotationScheduler != null) {
      rotationScheduler.shutdownNow();
    }
  }

  private static long millisUntilNextMidnight() {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay();
    return Duration.between(now, nextMidnight).toMillis();
  }

  private static synchronized void openFileHandler() {
    try {
      Path parent = Path.of(logFile).toAbsolutePath().getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      FileHandler handler = new FileHandler(logFile, true);
      handler.setFormatter(new OneLineLogFormatter());
      handler.setLevel(Level.ALL);
      Logger.getLogger("").addHandler(handler);
      fileHandler = handler;
    } catch (IOException e) {
      LOGGER.log(Level.SEVERE, "Could not open log file " + logFile, e);
    }
  }

  private static synchronized void rotate() {
    if (fileHandler == null) return;
    Logger.getLogger("").removeHandler(fileHandler);
    fileHandler.close();
    try {
      LogRotator.rotate(logFile, keepLogFiles);
    } catch (IOException e) {
      LOGGER.log(Level.SEVERE, "Could not rotate log file " + logFile, e);
    }
    openFileHandler();
  }
}
