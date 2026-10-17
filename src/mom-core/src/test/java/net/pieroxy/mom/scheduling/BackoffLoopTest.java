package net.pieroxy.mom.scheduling;

import net.pieroxy.mom.utils.scheduling.BackoffLoop;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BackoffLoopTest {

  @Test
  public void stopsPromptlyWhenInterruptedBeforeFirstRun() throws InterruptedException {
    AtomicInteger callCount = new AtomicInteger();
    BackoffLoop loop = new BackoffLoop(10_000, 60_000);

    Thread t = new Thread(() -> loop.run("test", callCount::incrementAndGet));
    // Interrupted before even starting: since the first cycle now runs without waiting,
    // interrupting after start() would be a race (the thread might already have launched the
    // task). Interrupting a Thread that hasn't started yet is valid and deterministic: the flag
    // is already set by the time the loop makes its first check.
    t.interrupt();
    t.start();
    t.join(2000);

    assertFalse("the thread must have stopped", t.isAlive());
    assertEquals("the task must never have run", 0, callCount.get());
  }

  @Test
  public void runsTheFirstCycleImmediatelyWithoutWaitingTheInitialDelay() throws InterruptedException {
    AtomicInteger callCount = new AtomicInteger();
    BackoffLoop loop = new BackoffLoop(60_000, 60_000); // deliberately huge delay

    Thread t = new Thread(() -> loop.run("test", () -> {
      callCount.incrementAndGet();
      Thread.currentThread().interrupt(); // just one cycle, then stop
    }));
    long start = System.currentTimeMillis();
    t.start();
    t.join(2000);
    long elapsed = System.currentTimeMillis() - start;

    assertFalse("the thread must have stopped", t.isAlive());
    assertEquals("the first cycle must have run without waiting the initial delay", 1, callCount.get());
    assertTrue("the first cycle should not have waited ~60s (elapsed=" + elapsed + "ms)", elapsed < 2000);
  }

  @Test
  public void usesTheProvidedWaiterInsteadOfSleeping() throws InterruptedException {
    AtomicInteger callCount = new AtomicInteger();
    List<Long> waitedMs = Collections.synchronizedList(new ArrayList<>());
    // A custom Waiter that returns immediately, ignoring delayMs: stands in for something like
    // IMAP IDLE returning early because it found something worth an early cycle.
    BackoffLoop loop = new BackoffLoop(60_000, 60_000, delayMs -> waitedMs.add(delayMs));

    Thread t = new Thread(() -> loop.run("test", () -> {
      if (callCount.incrementAndGet() >= 2) Thread.currentThread().interrupt(); // stop after the 2nd cycle
    }));
    long start = System.currentTimeMillis();
    t.start();
    t.join(2000);
    long elapsed = System.currentTimeMillis() - start;

    assertFalse("the thread must have stopped", t.isAlive());
    assertEquals("the custom waiter must have been asked to wait once, between the two cycles", 1, waitedMs.size());
    assertEquals("the waiter must have been told the configured delay", 60_000L, (long) waitedMs.get(0));
    assertTrue("a waiter that returns immediately must not block the loop (elapsed=" + elapsed + "ms)", elapsed < 2000);
  }

  @Test
  public void delayGrowsOnFailureAndResetsOnSuccess() throws InterruptedException {
    List<Long> timestamps = Collections.synchronizedList(new ArrayList<>());
    AtomicInteger callCount = new AtomicInteger();
    BackoffLoop loop = new BackoffLoop(50, 2000);

    Thread t = new Thread(() -> loop.run("test", () -> {
      int n = callCount.incrementAndGet();
      timestamps.add(System.currentTimeMillis());
      if (n <= 2) {
        throw new RuntimeException("boom");
      }
      if (n >= 4) {
        Thread.currentThread().interrupt();
      }
    }));
    t.start();
    t.join(5000);

    assertFalse("the thread must have stopped on its own", t.isAlive());
    assertEquals(4, callCount.get());

    long gapAfterFirstFailure = timestamps.get(1) - timestamps.get(0);   // initial delay (50ms)
    long gapAfterSecondFailure = timestamps.get(2) - timestamps.get(1); // doubled delay (100ms)
    long gapAfterSuccess = timestamps.get(3) - timestamps.get(2);       // reset to initial delay (50ms)

    assertTrue("the delay must grow after a failure (" + gapAfterFirstFailure + " -> " + gapAfterSecondFailure + ")",
            gapAfterSecondFailure > gapAfterFirstFailure * 1.5);
    assertTrue("the delay must return to the initial level after a success (" + gapAfterSecondFailure + " -> " + gapAfterSuccess + ")",
            gapAfterSuccess < gapAfterSecondFailure / 1.5);
  }
}
