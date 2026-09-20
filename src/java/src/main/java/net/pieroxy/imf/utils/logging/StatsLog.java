package net.pieroxy.imf.utils.logging;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Appends one JSON line per successful rule match to a per-UTC-day file
 * ({@code <statsDir>/stats-yyyy-MM-dd.json}) — a transactional record of what fired, as it
 * happens, meant for later aggregation rather than for a human to read live (see
 * {@code OneLineLogFormatter}/{@code LoggingBootstrap} for that). One JSON object per line
 * (rather than one JSON array for the whole file) so that adding an event is a plain append,
 * never a read-modify-write of the whole day's file — several account threads can call this
 * concurrently.
 * <p>
 * {@code statsDir} is null wherever no account context is available (see
 * {@code net.pieroxy.imf.rules.RuleContext#EMPTY}, used by most matcher/rule unit tests) — a
 * no-op in that case, same as {@code ReputationRegistryHolder}'s harmless default.
 */
public final class StatsLog {
  private final static Logger LOGGER = Logger.getLogger(StatsLog.class.getName());
  private final static Gson GSON = new Gson();
  private final static DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private final static DateTimeFormatter EVENT_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private StatsLog() {}

  public static synchronized void recordMatch(File statsDir, String matcherDescription) {
    if (statsDir == null) return;

    ZonedDateTime nowUtc = Instant.now().atZone(ZoneOffset.UTC);
    File file = new File(statsDir, "stats-" + FILE_DATE_FORMAT.format(nowUtc) + ".json");
    String line = GSON.toJson(new StatEvent(matcherDescription, EVENT_DATE_FORMAT.format(nowUtc) + "Z"));

    try {
      Files.createDirectories(statsDir.toPath());
      try (Writer writer = new FileWriter(file, true)) {
        writer.write(line);
        writer.write(System.lineSeparator());
      }
    } catch (IOException e) {
      LOGGER.log(Level.WARNING, "Could not write stats event to " + file, e);
    }
  }

  private static final class StatEvent {
    private final String matcher;
    private final String date;

    private StatEvent(String matcher, String date) {
      this.matcher = matcher;
      this.date = date;
    }
  }
}
