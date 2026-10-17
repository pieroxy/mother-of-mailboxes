package net.pieroxy.mom.utils;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Captures what a logger receives while open, keeping only records logged from the thread that
 * opened it: test classes run in parallel (see surefire's config in pom.xml) and loggers are
 * JVM-wide, so other tests' records reach the same logger concurrently.
 */
public final class LogCapture implements AutoCloseable {
  private final Logger logger;
  private final Thread owner = Thread.currentThread();
  private final List<LogRecord> records = new CopyOnWriteArrayList<>();
  private final Handler handler = new Handler() {
    @Override
    public void publish(LogRecord record) {
      if (Thread.currentThread() == owner) records.add(record);
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }
  };

  public LogCapture(Logger logger) {
    this.logger = logger;
    logger.addHandler(handler);
  }

  public List<LogRecord> getRecords() {
    return records;
  }

  /** Records whose raw message contains {@code text}. */
  public long count(String text) {
    return records.stream().filter(r -> r.getMessage() != null && r.getMessage().contains(text)).count();
  }

  @Override
  public void close() {
    logger.removeHandler(handler);
  }
}
