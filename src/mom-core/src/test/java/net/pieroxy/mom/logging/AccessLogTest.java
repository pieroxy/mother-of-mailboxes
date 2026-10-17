package net.pieroxy.mom.logging;

import net.pieroxy.mom.utils.logging.AccessLog;
import org.junit.Test;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.Assert.assertEquals;

public class AccessLogTest {
  private static final ZonedDateTime TIME = ZonedDateTime.of(2026, 10, 16, 11, 48, 6, 0, ZoneOffset.ofHours(2));

  @Test
  public void writesTheCombinedLogFormatFollowedByTheDuration() {
    assertEquals("203.0.113.7 - - [16/Oct/2026:11:48:06 +0200] \"GET /api/v1/config HTTP/1.1\" 404 - \"-\" \"curl/8.4\" 3ms",
        AccessLog.format("203.0.113.7", null, TIME, "GET", "/api/v1/config", null, "HTTP/1.1", 404, 0, null, "curl/8.4", 3));
  }

  @Test
  public void namesTheUserAndCountsTheBytes() {
    assertEquals("127.0.0.1 - admin [16/Oct/2026:11:48:06 +0200] \"GET /index.html HTTP/1.1\" 200 512 \"http://localhost/\" \"Firefox\" 1ms",
        AccessLog.format("127.0.0.1", "admin", TIME, "GET", "/index.html", null, "HTTP/1.1", 200, 512, "http://localhost/", "Firefox", 1));
  }

  @Test
  public void keepsTheQueryStringExceptForTheApi() {
    String probe = AccessLog.format("1.2.3.4", null, TIME, "GET", "/index.php", "cmd=id", "HTTP/1.1", 404, 0, null, null, 0);
    String api = AccessLog.format("1.2.3.4", null, TIME, "GET", "/api/Decisions", "input=%7B%22query%22%3A%22secret%22%7D", "HTTP/1.1", 200, 10, null, null, 0);

    assertEquals("\"GET /index.php?cmd=id HTTP/1.1\"", probe.substring(probe.indexOf('"'), probe.indexOf(" 404")));
    assertEquals("\"GET /api/Decisions HTTP/1.1\"", api.substring(api.indexOf('"'), api.indexOf(" 200")));
  }

  @Test
  public void anUnparseableRequestLineIsADash() {
    assertEquals("1.2.3.4 - - [16/Oct/2026:11:48:06 +0200] \"-\" 400 1972 \"-\" \"-\" 2ms",
        AccessLog.format("1.2.3.4", null, TIME, null, "", null, "HTTP/1.1", 400, 1972, null, null, 2));
  }

  @Test
  public void escapesQuotesBackslashesAndControlCharacters() {
    String line = AccessLog.format("1.2.3.4", null, TIME, "GET", "/", null, "HTTP/1.1", 200, 0, null, "evil\" \\ \n203.0.113.9 - - [fake]", 0);

    assertEquals("\"evil\\\" \\\\ \\x0a203.0.113.9 - - [fake]\"", line.substring(line.indexOf("\"evil"), line.lastIndexOf(' ')));
  }
}
