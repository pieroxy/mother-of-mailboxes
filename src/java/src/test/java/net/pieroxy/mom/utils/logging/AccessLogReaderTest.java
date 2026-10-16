package net.pieroxy.mom.utils.logging;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class AccessLogReaderTest {
  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private static final ZonedDateTime TIME = ZonedDateTime.of(2026, 10, 16, 11, 48, 6, 0, ZoneOffset.ofHours(2));

  private static String line(String path, int status) {
    return AccessLog.format("203.0.113.7", null, TIME, "GET", path, null, "HTTP/1.1", status, 12, null, "curl/8.4", 3);
  }

  private DailyLogFiles.DayFile file(String... lines) throws IOException {
    File f = new File(tmp.getRoot(), "access-2026-10-16.log");
    Files.writeString(f.toPath(), String.join("\n", lines) + "\n");
    return new DailyLogFiles.DayFile(LocalDate.of(2026, 10, 16), f, false);
  }

  private static List<String> requests(AccessLogReader.Page page) {
    return page.entries().stream().map(AccessLogReader.Entry::request).toList();
  }

  @Test
  public void parsesWhatAccessLogWrites() {
    String written = AccessLog.format("203.0.113.7", "admin", TIME, "GET", "/x", "a=1", "HTTP/1.1", 404, 0, "http://ref/", "evil\" agent", 7);

    AccessLogReader.Entry entry = AccessLogReader.parse(0, written);

    assertEquals("203.0.113.7", entry.remoteAddr());
    assertEquals("admin", entry.user());
    assertEquals("16/Oct/2026:11:48:06 +0200", entry.time());
    assertEquals("GET /x?a=1 HTTP/1.1", entry.request());
    assertEquals(404, entry.status());
    assertEquals("-", entry.bytes());
    assertEquals("http://ref/", entry.referer());
    assertEquals("evil\\\" agent", entry.userAgent());
    assertEquals(Long.valueOf(7), entry.durationMs());
  }

  @Test
  public void keepsALineThatDoesNotParseAsRaw() {
    AccessLogReader.Entry entry = AccessLogReader.parse(3, "not an access log line");

    assertEquals("not an access log line", entry.raw());
    assertEquals(0, entry.status());
    assertNull(entry.request());
  }

  @Test
  public void filtersByStatusRangeAndQuery() throws IOException {
    DailyLogFiles.DayFile file = file(line("/ok", 200), line("/missing", 404), line("/boom", 500));

    assertEquals(List.of("GET /boom HTTP/1.1", "GET /missing HTTP/1.1"),
        requests(AccessLogReader.read(file, new AccessLogReader.Filter(400, 0, null), null, 10)));
    assertEquals(List.of("GET /missing HTTP/1.1"),
        requests(AccessLogReader.read(file, new AccessLogReader.Filter(400, 499, null), null, 10)));
    assertEquals(List.of("GET /ok HTTP/1.1"),
        requests(AccessLogReader.read(file, new AccessLogReader.Filter(0, 0, "/OK"), null, 10)));
  }

  @Test
  public void pagesNewestFirstWithoutGapsOrDuplicates() throws IOException {
    DailyLogFiles.DayFile file = file(line("/1", 200), line("/2", 200), line("/3", 200), line("/4", 200), line("/5", 200));

    List<String> all = new ArrayList<>();
    Long cursor = null;
    do {
      AccessLogReader.Page page = AccessLogReader.read(file, AccessLogReader.Filter.NONE, cursor, 2);
      all.addAll(requests(page));
      cursor = page.nextCursor();
    } while (cursor != null);

    assertEquals(List.of("GET /5 HTTP/1.1", "GET /4 HTTP/1.1", "GET /3 HTTP/1.1", "GET /2 HTTP/1.1", "GET /1 HTTP/1.1"), all);
  }
}
