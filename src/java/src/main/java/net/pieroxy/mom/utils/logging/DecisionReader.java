package net.pieroxy.mom.utils.logging;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import net.jpountz.lz4.LZ4FrameInputStream;
import net.pieroxy.mom.api.metadata.TypeScriptNonConstEnum;
import net.pieroxy.mom.api.metadata.TypeScriptType;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Reads back what {@link DecisionLog} wrote, newest first, one page at a time.
 * <p>
 * A cursor is {@code <day>/<line index>}: the next page resumes at the line just before that
 * index in that day's file. Since a day file is only ever appended to (and compressed as is),
 * a line's index never changes, so a cursor stays valid while today's file keeps growing.
 */
public final class DecisionReader {
  private final static Logger LOGGER = Logger.getLogger(DecisionReader.class.getName());
  private final static Gson GSON = new Gson();
  private final static Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

  private DecisionReader() {}

  /** What happened to the message, as a whole. */
  @TypeScriptType
  @TypeScriptNonConstEnum
  public enum Outcome {
    /** A rule matched and stopped evaluation: its action took charge of the message. */
    HANDLED,
    /** No rule stopped evaluation (a {@code keepProcessing} rule may still have matched). */
    NOT_HANDLED,
    /** Learning example from which a rule was learned. */
    LEARNED,
    /** Learning example from which nothing could be learned. */
    NOT_LEARNED
  }

  /**
   * Only entries where a matcher reported this description, keyed the way the stats page keys
   * it (see {@link StatsReader#normalizeMatcherKey}), so its top matchers can link here.
   *
   * @param byType       compare by matcher type only (see {@link StatsReader#matcherTypeKey}).
   * @param blockingOnly only the rule that stopped evaluation counts.
   */
  public record MatcherFilter(String key, boolean byType, boolean blockingOnly) {
    boolean accepts(DecisionLog.Entry entry) {
      if (entry.rules() == null) return false;
      List<DecisionLog.RuleOutcome> candidates = blockingOnly
          ? (blockingRuleOf(entry) != null ? List.of(blockingRuleOf(entry)) : List.of())
          : entry.rules().stream().filter(r -> Boolean.TRUE.equals(r.matched())).toList();
      return candidates.stream().anyMatch(r -> r.matchDetail() != null && key.equals(keyOf(r.matchDetail())));
    }

    private String keyOf(String matchDetail) {
      String detailed = StatsReader.normalizeMatcherKey(matchDetail);
      return byType ? StatsReader.matcherTypeKey(detailed) : detailed;
    }
  }

  /**
   * Null or empty sets, and null or blank strings, don't filter.
   *
   * @param rule    only entries where a rule with exactly this description matched.
   * @param matcher null: no matcher filtering.
   * @param query   case- and accent-insensitive substring of the subject, From or Message-ID.
   */
  public record Filter(Set<DecisionLog.Trigger> triggers, Set<Outcome> outcomes, boolean errorsOnly, String rule,
                       MatcherFilter matcher, String query) {
    public static final Filter NONE = new Filter(null, null, false, null, null, null);

    boolean accepts(DecisionLog.Entry entry) {
      if (triggers != null && !triggers.isEmpty() && triggers.stream().noneMatch(t -> t.name().equals(entry.trigger()))) return false;
      if (outcomes != null && !outcomes.isEmpty() && !outcomes.contains(outcomeOf(entry))) return false;
      if (errorsOnly && !hasError(entry)) return false;
      if (rule != null && !rule.isBlank() && !ruleMatched(entry, rule)) return false;
      if (matcher != null && !matcher.accepts(entry)) return false;
      if (query != null && !query.isBlank()) {
        String needle = fold(query.trim());
        return fold(entry.subject()).contains(needle) || fold(entry.from()).contains(needle) || fold(entry.messageId()).contains(needle);
      }
      return true;
    }
  }

  /** @param nextCursor null when nothing older is left in the range. */
  public record Page(List<DecisionLog.Entry> entries, String nextCursor) {}

  public static Outcome outcomeOf(DecisionLog.Entry entry) {
    if (DecisionLog.Trigger.LEARNING.name().equals(entry.trigger())) {
      return entry.learnedRule() != null ? Outcome.LEARNED : Outcome.NOT_LEARNED;
    }
    return blockingRuleOf(entry) != null ? Outcome.HANDLED : Outcome.NOT_HANDLED;
  }

  /** The outcome of the rule that stopped evaluation, null if none did. */
  public static DecisionLog.RuleOutcome blockingRuleOf(DecisionLog.Entry entry) {
    if (entry.rules() == null) return null;
    return entry.rules().stream()
        .filter(r -> Boolean.TRUE.equals(r.matched()) && Boolean.FALSE.equals(r.keepProcessing()))
        .findFirst().orElse(null);
  }

  public static boolean hasError(DecisionLog.Entry entry) {
    return entry.rules() != null && entry.rules().stream().anyMatch(r -> r.exception() != null);
  }

  /**
   * Entries from {@code to} back to {@code from} (both inclusive), newest first.
   *
   * @param cursor     a previous page's {@code nextCursor}, or null for the newest entries.
   * @param limit      max entries returned.
   * @param scanBudget max lines examined, so that a filter matching little over a long range
   *                   can't make one call read everything: the page may then hold fewer than
   *                   {@code limit} entries, with a non-null {@code nextCursor}.
   */
  public static Page read(File decisionsDir, LocalDate from, LocalDate to, Filter filter, String cursor, int limit, int scanBudget) {
    LocalDate day = to;
    int before = Integer.MAX_VALUE;
    if (cursor != null) {
      int slash = cursor.indexOf('/');
      try {
        day = LocalDate.parse(cursor.substring(0, Math.max(slash, 0)));
        before = Integer.parseInt(cursor.substring(slash + 1));
      } catch (DateTimeParseException | NumberFormatException e) {
        throw new IllegalArgumentException("Invalid cursor: " + cursor);
      }
      if (day.isBefore(from) || day.isAfter(to) || before < 0) {
        throw new IllegalArgumentException("Cursor out of range: " + cursor);
      }
    }

    List<DecisionLog.Entry> entries = new ArrayList<>();
    int scanned = 0;
    for (; !day.isBefore(from); day = day.minusDays(1), before = Integer.MAX_VALUE) {
      List<String> lines = readDay(decisionsDir, day);
      for (int i = Math.min(before, lines.size()) - 1; i >= 0; i--) {
        if (entries.size() >= limit || scanned >= scanBudget) {
          return new Page(entries, day + "/" + (i + 1));
        }
        scanned++;
        DecisionLog.Entry entry = parse(lines.get(i));
        if (entry != null && filter.accepts(entry)) entries.add(entry);
      }
    }
    return new Page(entries, null);
  }

  /** Plain file for today (and briefly for older days, until rotated), {@code .lz4} otherwise. */
  private static List<String> readDay(File decisionsDir, LocalDate day) {
    if (decisionsDir == null) return List.of();
    File plain = new File(decisionsDir, DecisionLog.FILE_PREFIX + day + ".json");
    File compressed = new File(decisionsDir, plain.getName() + ".lz4");
    try {
      if (plain.isFile()) {
        try {
          return Files.readAllLines(plain.toPath(), StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
          // Compressed and deleted by a concurrent rotation: read the .lz4 instead.
        }
      }
      if (compressed.isFile()) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            new LZ4FrameInputStream(new BufferedInputStream(new FileInputStream(compressed))), StandardCharsets.UTF_8))) {
          return reader.lines().toList();
        }
      }
    } catch (IOException e) {
      LOGGER.log(Level.WARNING, "Could not read decision log for " + day + " in " + decisionsDir, e);
    }
    return List.of();
  }

  /** Null for a blank or corrupt line (e.g. truncated by a crash mid-write). */
  private static DecisionLog.Entry parse(String line) {
    if (line.isBlank()) return null;
    try {
      return GSON.fromJson(line, DecisionLog.Entry.class);
    } catch (JsonParseException e) {
      LOGGER.log(Level.WARNING, "Skipping malformed decision log line: " + line, e);
      return null;
    }
  }

  private static boolean ruleMatched(DecisionLog.Entry entry, String rule) {
    return entry.rules() != null && entry.rules().stream().anyMatch(r -> Boolean.TRUE.equals(r.matched()) && rule.equals(r.rule()));
  }

  private static String fold(String s) {
    if (s == null) return "";
    return COMBINING_MARKS.matcher(Normalizer.normalize(s, Normalizer.Form.NFKD)).replaceAll("").toLowerCase(Locale.ROOT);
  }
}
