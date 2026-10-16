package net.pieroxy.mom.utils.logging;

import net.jpountz.lz4.LZ4FrameInputStream;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads back the application log written through {@link OneLineLogFormatter}: the current file and
 * the archives {@link LogRotator} made of it, newest entries first, one page at a time.
 * <p>
 * An entry is a header line plus the lines that follow it until the next header (a stack trace,
 * a multi-line message). Lines before the first header, e.g. written by something bypassing the
 * formatter, form an entry with no timestamp nor level.
 * <p>
 * A cursor is an entry's index in its file: the next page holds the matching entries before it.
 * Files are only ever appended to, so an index never changes while the file exists. Each call
 * streams the file up to the cursor, keeping at most {@code limit} entries in memory.
 */
public final class AppLogReader {
  public static final String CURRENT = "current";
  private static final Pattern HEADER = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}) (\\d{4}) +([A-Z]+) +(\\S+) ?(.*)$");
  private static final Pattern ARCHIVE_INDEX = Pattern.compile("\\.(\\d+)\\.lz4$");
  /** Caps a single entry's continuation lines, against a runaway stack trace or dump. */
  private static final int MAX_DETAILS_CHARS = 50_000;

  private AppLogReader() {}

  /** @param id {@link #CURRENT}, or an archive's index (1 = the most recent). */
  public record LogFile(String id, File file, boolean compressed) {}

  /**
   * @param index     position in its file, used as a cursor.
   * @param timestamp "yyyy-MM-dd HH:mm:ss", server local time; null for lines before the first header.
   * @param runId     the instance ID, which changes at every restart.
   * @param details   continuation lines, null if none.
   */
  public record Entry(long index, String timestamp, String runId, String level, String source, String message, String details) {}

  /**
   * @param minLevel null: any level. Entries with no level always pass.
   * @param query    case-insensitive substring of the source, message or details; null or blank: any.
   */
  public record Filter(Level minLevel, String query) {
    public static final Filter NONE = new Filter(null, null);

    boolean accepts(Entry entry) {
      if (minLevel != null && entry.level() != null && levelValue(entry.level()) < minLevel.intValue()) return false;
      if (query == null || query.isBlank()) return true;
      String needle = query.trim().toLowerCase(Locale.ROOT);
      return contains(entry.source(), needle) || contains(entry.message(), needle) || contains(entry.details(), needle);
    }

    private static boolean contains(String haystack, String needle) {
      return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static int levelValue(String level) {
      try {
        return Level.parse(level).intValue();
      } catch (IllegalArgumentException e) {
        return Integer.MAX_VALUE;
      }
    }
  }

  /** @param nextCursor null when no older matching entry is left in the file. */
  public record Page(List<Entry> entries, Long nextCursor) {}

  /** The current file (if it exists) then the archives, most recent first. Empty when logging to a file isn't configured. */
  public static List<LogFile> listFiles(String logFile) {
    List<LogFile> files = new ArrayList<>();
    if (logFile == null) return files;
    File current = new File(logFile);
    if (current.isFile()) files.add(new LogFile(CURRENT, current, false));
    File[] siblings = current.getAbsoluteFile().getParentFile().listFiles();
    if (siblings == null) return files;
    List<LogFile> archives = new ArrayList<>();
    for (File sibling : siblings) {
      if (!sibling.isFile() || !sibling.getName().startsWith(current.getName() + ".")) continue;
      Matcher m = ARCHIVE_INDEX.matcher(sibling.getName());
      if (m.find() && sibling.getName().equals(current.getName() + "." + m.group(1) + ".lz4")) {
        archives.add(new LogFile(m.group(1), sibling, true));
      }
    }
    archives.sort((a, b) -> Integer.compare(Integer.parseInt(a.id()), Integer.parseInt(b.id())));
    files.addAll(archives);
    return files;
  }

  /** Newest matching entries before {@code cursor} (null: from the end of the file), newest first. */
  public static Page read(LogFile file, Filter filter, Long cursor, int limit) throws IOException {
    Deque<Entry> kept = new ArrayDeque<>();
    boolean olderMatches = false;
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(open(file), StandardCharsets.UTF_8))) {
      EntryBuilder pending = null;
      long index = 0;
      String line;
      while ((line = reader.readLine()) != null) {
        Matcher header = HEADER.matcher(line);
        if (header.matches() || pending == null) {
          if (pending != null) {
            if (cursor != null && index >= cursor) break;
            olderMatches |= keep(pending.build(index++), filter, kept, limit);
          }
          pending = header.matches() ? new EntryBuilder(header) : new EntryBuilder(line);
        } else {
          pending.addDetail(line);
        }
      }
      if (pending != null && (cursor == null || index < cursor)) {
        olderMatches |= keep(pending.build(index), filter, kept, limit);
      }
    }
    List<Entry> newestFirst = new ArrayList<>(kept);
    Collections.reverse(newestFirst);
    Long nextCursor = olderMatches && !kept.isEmpty() ? kept.peekFirst().index() : null;
    return new Page(newestFirst, nextCursor);
  }

  /** @return true if a matching entry had to be dropped to stay within {@code limit}. */
  private static boolean keep(Entry entry, Filter filter, Deque<Entry> kept, int limit) {
    if (!filter.accepts(entry)) return false;
    kept.addLast(entry);
    if (kept.size() <= limit) return false;
    kept.removeFirst();
    return true;
  }

  private static InputStream open(LogFile file) throws IOException {
    InputStream in = new BufferedInputStream(new FileInputStream(file.file()));
    return file.compressed() ? new LZ4FrameInputStream(in) : in;
  }

  private static final class EntryBuilder {
    private final String timestamp;
    private final String runId;
    private final String level;
    private final String source;
    private final String message;
    private StringBuilder details;
    private boolean truncated;

    EntryBuilder(Matcher header) {
      timestamp = header.group(1);
      runId = header.group(2);
      level = header.group(3);
      source = header.group(4);
      message = header.group(5);
    }

    EntryBuilder(String unformattedLine) {
      timestamp = null;
      runId = null;
      level = null;
      source = null;
      message = unformattedLine;
    }

    void addDetail(String line) {
      if (truncated) return;
      if (details == null) details = new StringBuilder();
      else details.append('\n');
      if (details.length() + line.length() > MAX_DETAILS_CHARS) {
        details.append("… (truncated)");
        truncated = true;
        return;
      }
      details.append(line);
    }

    Entry build(long index) {
      return new Entry(index, timestamp, runId, level, source, message, details == null ? null : details.toString());
    }
  }
}
