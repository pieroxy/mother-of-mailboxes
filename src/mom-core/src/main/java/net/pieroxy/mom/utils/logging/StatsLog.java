package net.pieroxy.mom.utils.logging;

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
 * Appends one JSON line per event (a matcher firing, or a whole message finishing its run through
 * the rule chain) to a per-UTC-day file ({@code <statsDir>/stats-yyyy-MM-dd.json}) — a
 * transactional record of what happened, as it happens, meant for later aggregation rather than
 * for a human to read live (see {@code OneLineLogFormatter}/{@code LoggingBootstrap} for that).
 * {@code statsDir} is account-scoped ({@code MailAccount} builds it as {@code <dataFolder>/logs/
 * <accountDisplayName>}, same layout as {@code ClassifierCorpusStore}) so lines never need an
 * account field of their own and one account's stats can't interleave with another's.
 * One JSON object per line (rather than one JSON array for the whole file) so that adding an
 * event is a plain append, never a read-modify-write of the whole day's file — several account
 * threads can call this concurrently.
 * <p>
 * {@code statsDir} is null wherever no account context is available (see
 * {@code net.pieroxy.mom.rules.RuleContext#EMPTY}, used by most matcher/rule unit tests) — a
 * no-op in that case, same as {@code ReputationRegistryHolder}'s harmless default.
 */
public final class StatsLog {
  private final static Logger LOGGER = Logger.getLogger(StatsLog.class.getName());
  private final static Gson GSON = new Gson();
  private final static DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private final static DateTimeFormatter EVENT_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  /** The chain's verdict for one processed message — see {@link #recordProcessed}. */
  public enum ProcessResult { MATCH, PASS }

  private StatsLog() {}

  /** One entry per matcher that actually matched (see {@code Rule#apply}). */
  public static void recordMatch(File statsDir, String matcherDescription) {
    write(statsDir, new StatEvent("MATCH", matcherDescription, null, null));
  }

  /**
   * One entry per message that finished its run through the whole rule chain (manual config, then
   * learned rules). {@code result} is {@code MATCH} if some rule ultimately blocked it —
   * {@code matcherDescription} then names that rule, same description as its own separate
   * {@code MATCH} entry (deliberately redundant: a message blocked by a single, non-{@code
   * keepProcessing} rule — by far the common case — gets its "what and how long" in this one
   * line, no need to cross-reference the two) — or {@code PASS} if the chain ran to completion
   * without ever blocking (a {@code keepProcessing} rule may still have matched along the way;
   * {@code matcherDescription} is null here, see its own {@code MATCH} entry instead).
   */
  public static void recordProcessed(File statsDir, ProcessResult result, String matcherDescription, long processedMs) {
    write(statsDir, new StatEvent("PROCESSED", matcherDescription, result.name(), processedMs));
  }

  private static synchronized void write(File statsDir, StatEvent event) {
    if (statsDir == null) return;

    ZonedDateTime nowUtc = Instant.now().atZone(ZoneOffset.UTC);
    File file = new File(statsDir, "stats-" + FILE_DATE_FORMAT.format(nowUtc) + ".json");
    event.date = EVENT_DATE_FORMAT.format(nowUtc) + "Z";
    String line = GSON.toJson(event);

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

  /** Null fields are omitted from the JSON output (default Gson behavior) — never emitted as "field":null. */
  private static final class StatEvent {
    private final String type;
    private final String matcher;
    private final String result;
    private final Long processedMs;
    private String date;

    private StatEvent(String type, String matcher, String result, Long processedMs) {
      this.type = type;
      this.matcher = matcher;
      this.result = result;
      this.processedMs = processedMs;
    }
  }
}
