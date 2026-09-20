package net.pieroxy.imf.utils.scheduling;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Generic "run / wait" loop with exponential backoff on failure (reset on the next success) and
 * clean shutdown on interruption. The first cycle runs immediately (no wait before the very
 * first run); the wait only happens between cycles, via a pluggable {@link Waiter} (a plain
 * {@code Thread.sleep} by default) — a custom one (e.g. IMAP IDLE) can return before delayMs is
 * up when something worth an early cycle happens. Knows nothing about the work it runs, which
 * makes it testable independently of any mail account.
 */
public class BackoffLoop {
  public interface Task {
    void run() throws Exception;
  }

  /** How the loop waits between cycles. Must return by delayMs at the latest. */
  public interface Waiter {
    void await(long delayMs) throws InterruptedException;
  }

  private final static Logger LOGGER = Logger.getLogger(BackoffLoop.class.getName());

  private final long initialDelayMs;
  private final long maxDelayMs;
  private final Waiter waiter;

  public BackoffLoop(long initialDelayMs, long maxDelayMs) {
    this(initialDelayMs, maxDelayMs, Thread::sleep);
  }

  public BackoffLoop(long initialDelayMs, long maxDelayMs, Waiter waiter) {
    this.initialDelayMs = initialDelayMs;
    this.maxDelayMs = maxDelayMs;
    this.waiter = waiter;
  }

  public void run(String name, Task task) {
    long delayMs = initialDelayMs;
    boolean firstRun = true;
    while (!Thread.currentThread().isInterrupted()) {
      if (!firstRun) {
        try {
          waiter.await(delayMs);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
      firstRun = false;
      try {
        task.run();
        delayMs = initialDelayMs;
      } catch (Exception e) {
        delayMs = Math.min(delayMs * 2, maxDelayMs);
        LOGGER.log(Level.WARNING, name + ": " + e.getMessage() + ". Next retry in " + delayMs + "ms.", e);
      }
    }
    LOGGER.info(name + " stopped.");
  }
}
