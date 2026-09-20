package net.pieroxy.imf.logging;

import net.pieroxy.imf.utils.logging.OneLineLogFormatter;
import org.junit.Test;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OneLineLogFormatterTest {

  private static final Pattern LINE_PATTERN = Pattern.compile(
      "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} (\\d{4}) ([A-Z ]{7}) (.{30}) (.*)\n$");

  @Test
  public void padsShortLevelAndSourceOnTheLeft() {
    OneLineLogFormatter formatter = new OneLineLogFormatter();
    LogRecord record = new LogRecord(Level.INFO, "Starting account budget@nullbird.com");
    record.setSourceClassName("net.pieroxy.imf.rules.MailAccount");
    record.setSourceMethodName("run");

    Matcher m = LINE_PATTERN.matcher(formatter.format(record));
    assertTrue(m.matches());
    assertEquals("   INFO", m.group(2));
    assertEquals("roxy.imf.rules.MailAccount/run", m.group(3));
    assertEquals("Starting account budget@nullbird.com", m.group(4));
  }

  @Test
  public void doesNotPadWhenLevelExactlyFillsTheField() {
    OneLineLogFormatter formatter = new OneLineLogFormatter();
    LogRecord record = new LogRecord(Level.WARNING, "boom");
    record.setSourceClassName("x");
    record.setSourceMethodName("y");

    Matcher m = LINE_PATTERN.matcher(formatter.format(record));
    assertTrue(m.matches());
    assertEquals("WARNING", m.group(2));
  }

  @Test
  public void truncatesLongSourceKeepingTheTail() {
    OneLineLogFormatter formatter = new OneLineLogFormatter();
    LogRecord record = new LogRecord(Level.SEVERE, "Grabbing free space for /media/pieroxy/FAC5-5F2E");
    record.setSourceClassName("net.pieroxy.somewhere.grabbers.JavaSystemViewGrabber");
    record.setSourceMethodName("sys");

    String line = formatter.format(record);
    assertTrue(line.contains(" SEVERE bers.JavaSystemViewGrabber/sys "));
  }

  @Test
  public void runIdStaysStableAcrossFormatterInstances() {
    LogRecord record = new LogRecord(Level.INFO, "hello");
    record.setSourceClassName("x");
    record.setSourceMethodName("y");

    String first = new OneLineLogFormatter().format(record);
    String second = new OneLineLogFormatter().format(record);

    Matcher m1 = LINE_PATTERN.matcher(first);
    Matcher m2 = LINE_PATTERN.matcher(second);
    assertTrue(m1.matches());
    assertTrue(m2.matches());
    assertEquals(m1.group(1), m2.group(1));
  }

  @Test
  public void appendsStackTraceWhenThrowableIsPresent() {
    OneLineLogFormatter formatter = new OneLineLogFormatter();
    LogRecord record = new LogRecord(Level.SEVERE, "failed");
    record.setSourceClassName("x");
    record.setSourceMethodName("y");
    record.setThrown(new RuntimeException("boom"));

    String line = formatter.format(record);
    assertTrue(line.contains("java.lang.RuntimeException: boom"));
  }
}
