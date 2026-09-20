package net.pieroxy.imf.utils.logging;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * Formats each log entry on a single line: timestamp, random instance ID (generated once at
 * process startup, to immediately spot in the logs if two instances are running at the same
 * time), level and origin (class/method) aligned to a fixed width (truncated from the left if
 * they overflow, to always keep the most significant part), then the message.
 */
public final class OneLineLogFormatter extends Formatter {
  private static final DateTimeFormatter TIMESTAMP_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  private static final int LEVEL_WIDTH = 7;
  private static final int SOURCE_WIDTH = 30;
  private static final String RUN_ID = String.format("%04d", ThreadLocalRandom.current().nextInt(10000));

  @Override
  public String format(LogRecord record) {
    String timestamp =
        TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(record.getMillis()).atZone(ZoneId.systemDefault()));
    String source = record.getSourceClassName() + "/" + record.getSourceMethodName();

    StringBuilder sb = new StringBuilder();
    sb.append(timestamp).append(' ')
        .append(RUN_ID).append(' ')
        .append(rightAlign(record.getLevel().getName(), LEVEL_WIDTH)).append(' ')
        .append(rightAlign(source, SOURCE_WIDTH)).append(' ')
        .append(formatMessage(record)).append('\n');

    if (record.getThrown() != null) {
      StringWriter sw = new StringWriter();
      record.getThrown().printStackTrace(new PrintWriter(sw));
      sb.append(sw);
    }
    return sb.toString();
  }

  private static String rightAlign(String s, int width) {
    if (s == null) s = "";
    return s.length() >= width ? s.substring(s.length() - width) : " ".repeat(width - s.length()) + s;
  }
}
