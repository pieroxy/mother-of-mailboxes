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
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads back the {@link AccessLog}, one day's file at a time, newest entries first. A cursor is a
 * line's index in its file, like {@link AppLogReader}'s.
 */
public final class AccessLogReader {
  private static final String QUOTED = "\"((?:[^\"\\\\]|\\\\.)*)\"";
  private static final Pattern LINE = Pattern.compile(
      "^(\\S+) \\S+ (\\S+) \\[([^\\]]+)] " + QUOTED + " (\\d{3}) (\\S+) " + QUOTED + " " + QUOTED + "(?: (\\d+)ms)?$");

  private AccessLogReader() {}

  /** The access log's daily files, most recent first; empty when MOM doesn't log to a file. */
  public static List<DailyLogFiles.DayFile> listFiles() {
    return AccessLog.DAILY_FILES.list(AccessLog.directory());
  }

  /**
   * Fields as written, still escaped (see {@link AccessLog#format}); "-" for an absent one. A line
   * that doesn't parse only has {@code index} and {@code raw}.
   *
   * @param status     0 for a line that doesn't parse.
   * @param durationMs null if absent.
   */
  public record Entry(long index, String raw, String remoteAddr, String user, String time, String request, int status,
                      String bytes, String referer, String userAgent, Long durationMs) {}

  /**
   * @param minStatus 0: no lower bound.
   * @param maxStatus 0: no upper bound.
   * @param query     case-insensitive substring of the whole line; null or blank: any.
   */
  public record Filter(int minStatus, int maxStatus, String query) {
    public static final Filter NONE = new Filter(0, 0, null);

    boolean accepts(Entry entry) {
      if (minStatus > 0 && entry.status() < minStatus) return false;
      if (maxStatus > 0 && entry.status() > maxStatus) return false;
      return query == null || query.isBlank() || entry.raw().toLowerCase(Locale.ROOT).contains(query.trim().toLowerCase(Locale.ROOT));
    }
  }

  /** @param nextCursor null when no older matching entry is left in the file. */
  public record Page(List<Entry> entries, Long nextCursor) {}

  /** Newest matching entries before {@code cursor} (null: from the end of the file), newest first. */
  public static Page read(DailyLogFiles.DayFile file, Filter filter, Long cursor, int limit) throws IOException {
    NewestFirst<Entry> page = new NewestFirst<>(limit, Entry::index);
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(open(file), StandardCharsets.UTF_8))) {
      long index = 0;
      String line;
      while ((line = reader.readLine()) != null && (cursor == null || index < cursor)) {
        Entry entry = parse(index++, line);
        if (filter.accepts(entry)) page.add(entry);
      }
    }
    return new Page(page.newestFirst(), page.nextCursor());
  }

  static Entry parse(long index, String line) {
    Matcher m = LINE.matcher(line);
    if (!m.matches()) return new Entry(index, line, null, null, null, null, 0, null, null, null, null);
    return new Entry(index, line, m.group(1), m.group(2), m.group(3), m.group(4), Integer.parseInt(m.group(5)), m.group(6),
        m.group(7), m.group(8), m.group(9) == null ? null : Long.parseLong(m.group(9)));
  }

  private static InputStream open(DailyLogFiles.DayFile file) throws IOException {
    File f = file.file();
    InputStream in = new BufferedInputStream(new FileInputStream(f));
    return file.compressed() ? new LZ4FrameInputStream(in) : in;
  }
}
