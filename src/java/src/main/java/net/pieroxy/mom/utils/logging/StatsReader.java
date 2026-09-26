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
import java.util.regex.Pattern;

/**
 * Reads back what {@link StatsLog} wrote — one pass per day over {@code <statsDir>/
 * stats-yyyy-MM-dd.json} for a date range, aggregating each day's {@code PROCESSED} events into a
 * {@link DailyStats} and counting matcher firings two ways (see {@link StatsRange}):
 * {@code matcherCounts} from every {@code MATCH} event (a matcher that fired, whether or not it
 * ultimately blocked the message — see {@code MailFilterRuleConfiguration#isKeepProcessing()}),
 * and {@code blockingMatcherCounts} from just the {@code PROCESSED} events whose {@code result}
 * is {@code MATCH} (the one rule, if any, that actually stopped the chain for that message — see
 * {@code StatsLog#recordProcessed}). A missing day's file (nothing processed that day, or before
 * the account existed) contributes a zeroed {@link DailyStats} rather than a gap, so a caller can
 * plot a continuous range without special-casing holes.
 */
public final class StatsReader {
  private final static Logger LOGGER = Logger.getLogger(StatsReader.class.getName());
  private final static Gson GSON = new Gson();
  private final static DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  // Matches e.g. "BodyClassifierMatcher(score=0.999)" and "IpReputationMatcher[list](score=0.87)"
  // — the exact score varies message to message, so grouping by matcher identity means dropping
  // it: "BodyClassifierMatcher(score=1.0)" and "...(score=0.999)" are the same matcher having
  // fired twice, not two different matchers each firing once.
  private final static Pattern SCORE_VALUE = Pattern.compile("score=-?\\d+(\\.\\d+)?");

  private StatsReader() {}

  /** One calendar day's worth of {@code PROCESSED} events. */
  public record DailyStats(LocalDate date, long messagesProcessed, long messagesMatched, double avgProcessingMs) {}

  /**
   * {@code days} covers every day in the requested range, in order; both count maps are summed
   * across all of them. {@code matcherCounts}: every matcher firing (see {@code MATCH} events).
   * {@code blockingMatcherCounts}: only the matcher that ended processing for a message (see
   * {@code PROCESSED} events with {@code result=MATCH}) — a strict subset by identity (every
   * blocking matcher also fired, and so is also counted in {@code matcherCounts}), but usually a
   * much smaller one, since a {@code keepProcessing} rule fires without ever blocking anything.
   */
  public record StatsRange(List<DailyStats> days, Map<String, Long> matcherCounts, Map<String, Long> blockingMatcherCounts) {}

  /** {@code from}/{@code to} are inclusive. statsDir may not exist yet (a brand new account) — treated as all-zero. */
  public static StatsRange read(File statsDir, LocalDate from, LocalDate to) {
    List<DailyStats> days = new ArrayList<>();
    Map<String, Long> matcherCounts = new LinkedHashMap<>();
    Map<String, Long> blockingMatcherCounts = new LinkedHashMap<>();
    for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
      days.add(readDay(statsDir, day, matcherCounts, blockingMatcherCounts));
    }
    return new StatsRange(days, matcherCounts, blockingMatcherCounts);
  }

  private static DailyStats readDay(File statsDir, LocalDate day, Map<String, Long> matcherCounts, Map<String, Long> blockingMatcherCounts) {
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
              String matcher = stringField(event, "matcher");
              if ("MATCH".equals(stringField(event, "result"))) {
                matched++;
                if (matcher != null) blockingMatcherCounts.merge(normalizeMatcherKey(matcher), 1L, Long::sum);
              }
              if (event.has("processedMs")) totalMs += event.get("processedMs").getAsLong();
            } else if ("MATCH".equals(type)) {
              String matcher = stringField(event, "matcher");
              if (matcher != null) matcherCounts.merge(normalizeMatcherKey(matcher), 1L, Long::sum);
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

  /** Drops a "score=<value>" reading from a matcher description — see {@link #SCORE_VALUE}. */
  private static String normalizeMatcherKey(String matcher) {
    return SCORE_VALUE.matcher(matcher).replaceAll("score");
  }
}
