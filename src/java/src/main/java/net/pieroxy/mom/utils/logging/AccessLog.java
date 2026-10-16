package net.pieroxy.mom.utils.logging;

import java.io.File;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The web server's access log: one line per HTTP request, in the Combined Log Format (as Apache
 * or nginx write it, so fail2ban, GoAccess and the like read it as is) followed by the time spent
 * answering. One file per local day, {@code access-yyyy-MM-dd.log}, next to the application log
 * and kept as long ({@link LoggingBootstrap#getKeepLogFiles()}).
 */
public final class AccessLog {
  private final static Logger LOGGER = Logger.getLogger(AccessLog.class.getName());
  private final static DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z", Locale.ENGLISH);
  final static DailyLogFiles DAILY_FILES = new DailyLogFiles("access-", ".log");

  private AccessLog() {}

  /** Appends {@code line}, written at {@code time}, to that day's file. Does nothing when MOM doesn't log to a file. */
  public static synchronized void record(ZonedDateTime time, String line) {
    String logFile = LoggingBootstrap.getLogFile();
    if (logFile == null) return;
    File dir = new File(logFile).getAbsoluteFile().getParentFile();
    try {
      DAILY_FILES.append(dir, time.toLocalDate(), line, LoggingBootstrap.getKeepLogFiles());
    } catch (IOException e) {
      LOGGER.log(Level.WARNING, "Could not write access log entry to " + dir, e);
    }
  }

  /**
   * {@code host - user [time] "request line" status bytes "referer" "user agent" durationms}. The
   * query string is dropped for {@code /api/} paths: it holds the endpoint's JSON input, which can
   * carry mail metadata (e.g. a decision log search). Quotes, backslashes and control characters
   * are escaped, so a crafted header can't forge a field or a line.
   *
   * @param user  null when the request carried no valid session.
   * @param bytes body bytes sent; 0 is written "-".
   */
  public static String format(String remoteAddr, String user, ZonedDateTime time, String method, String uri, String query,
                              String protocol, int status, long bytes, String referer, String userAgent, long durationMs) {
    String target = uri == null ? "" : uri;
    if (query != null && !query.isEmpty() && !target.startsWith("/api/")) target += "?" + query;
    // No method: Tomcat couldn't parse the request line at all.
    String requestLine = method == null || method.isEmpty() ? "-" : method + " " + target + (protocol == null ? "" : " " + protocol);
    return orDash(remoteAddr) + " - " + escape(orDash(user)) + " [" + TIME_FORMAT.format(time) + "] "
        + quoted(requestLine) + " " + status + " " + (bytes > 0 ? String.valueOf(bytes) : "-") + " "
        + quoted(orDash(referer)) + " " + quoted(orDash(userAgent)) + " " + durationMs + "ms";
  }

  private static String orDash(String s) {
    return s == null || s.isEmpty() ? "-" : s;
  }

  private static String quoted(String s) {
    return "\"" + escape(s) + "\"";
  }

  private static String escape(String s) {
    StringBuilder sb = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '"' || c == '\\') sb.append('\\').append(c);
      else if (c < 0x20 || c == 0x7f) sb.append(String.format("\\x%02x", (int) c));
      else sb.append(c);
    }
    return sb.toString();
  }
}
