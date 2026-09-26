package net.pieroxy.mom.utils.logging;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads back what {@link StatsLog} wrote — one pass per day over {@code <statsDir>/
 * stats-yyyy-MM-dd.json} for a date range, aggregating each day's {@code PROCESSED} events into a
 * {@link DailyStats} and every day's {@code MATCH} events into a shared matcher-firing count
 * (see {@link StatsRange}). A missing day's file (nothing processed that day, or before the
 * account existed) contributes a zeroed {@link DailyStats} rather than a gap, so a caller can
 * plot a continuous range without special-casing holes.
 */
public final class StatsReader {
  private final static Logger LOGGER = Logger.getLogger(StatsReader.class.getName());
  private final static Gson GSON = new Gson();
  private final static DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private StatsReader() {}

  /** One calendar day's worth of {@code PROCESSED} events. */
  public record DailyStats(LocalDate date, long messagesProcessed, long messagesMatched, double avgProcessingMs) {}

  /** {@code days} covers every day in the requested range, in order; {@code matcherCounts} is summed across all of them. */
  public record StatsRange(List<DailyStats> days, Map<String, Long> matcherCounts) {}

  /** {@code from}/{@code to} are inclusive. statsDir may not exist yet (a brand new account) — treated as all-zero. */
  public static StatsRange read(File statsDir, LocalDate from, LocalDate to) {
    List<DailyStats> days = new ArrayList<>();
    Map<String, Long> matcherCounts = new LinkedHashMap<>();
    for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
      days.add(readDay(statsDir, day, matcherCounts));
    }
    return new StatsRange(days, matcherCounts);
  }

  private static DailyStats readDay(File statsDir, LocalDate day, Map<String, Long> matcherCounts) {
    File file = new File(statsDir, "stats-" + FILE_DATE_FORMAT.format(day) + ".json");
    long processed = 0, matched = 0, totalMs = 0;
    if (file.isFile()) {
      try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.isBlank()) continue;
          try {
            JsonObject event = GSON.fromJson(line, JsonObject.class);
            String type = stringField(event, "type");
            if ("PROCESSED".equals(type)) {
              processed++;
              if ("MATCH".equals(stringField(event, "result"))) matched++;
              if (event.has("processedMs")) totalMs += event.get("processedMs").getAsLong();
            } else if ("MATCH".equals(type)) {
              String matcher = stringField(event, "matcher");
              if (matcher != null) matcherCounts.merge(matcher, 1L, Long::sum);
            }
          } catch (JsonParseException e) {
            // One line is one event (see StatsLog): a single truncated/corrupt line (e.g. from a
            // crash mid-write) must not cost every other line already safely on disk that day.
            LOGGER.log(Level.WARNING, "Skipping malformed stats line in " + file, e);
          }
        }
      } catch (IOException e) {
        LOGGER.log(Level.WARNING, "Could not read stats file " + file, e);
      }
    }
    double avgMs = processed > 0 ? (double) totalMs / processed : 0;
    return new DailyStats(day, processed, matched, avgMs);
  }

  private static String stringField(JsonObject event, String field) {
    return event.has(field) ? event.get(field).getAsString() : null;
  }
}
