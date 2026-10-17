package net.pieroxy.mom.utils;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * At most {@code maxHits} hits per key within any sliding window of {@code milliseconds}.
 * Expired hits are dropped while hitting: the key's own on every hit, every other key's at most
 * once per window, so memory stays bounded by the keys hit during the last window.
 */
public class RateLimiter {
  private final long milliseconds;
  private final int maxHits;

  private final Map<String, Deque<Long>> data = new HashMap<>();
  private long lastSweep;

  public RateLimiter(int milliseconds, int maxHits) {
    this.milliseconds = milliseconds;
    this.maxHits = maxHits;
  }

  /**
   * Keys are compared trimmed and case-insensitively.
   *
   * @throws RateExceededException if {@code key} already has {@code maxHits} hits in the window. Adds no hit.
   */
  public synchronized void check(String key) throws RateExceededException {
    long now = System.currentTimeMillis();
    Deque<Long> hits = hitsOf(normalize(key), now);
    if (hits != null && hits.size() >= maxHits) throw new RateExceededException(hits.peekFirst() + milliseconds - now);
  }

  public synchronized void hit(String key) {
    long now = System.currentTimeMillis();
    String normalized = normalize(key);
    if (hitsOf(normalized, now) == null) data.put(normalized, new ArrayDeque<>());
    data.get(normalized).addLast(now);
  }

  public synchronized void reset(String key) {
    data.remove(normalize(key));
  }

  /** {@code key}'s hits still in the window, null if none; also sweeps every key at most once per window. */
  private Deque<Long> hitsOf(String key, long now) {
    long expired = now - milliseconds;
    if (now - lastSweep >= milliseconds) {
      gc(expired);
      lastSweep = now;
    }
    Deque<Long> hits = data.get(key);
    if (hits == null) return null;
    trim(hits, expired);
    if (hits.isEmpty()) {
      data.remove(key);
      return null;
    }
    return hits;
  }

  private static String normalize(String key) {
    return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
  }

  public synchronized int getNbKeys() {
    return data.size();
  }

  public synchronized int getNbTimeStamps() {
    return data.values().stream().mapToInt(Deque::size).sum();
  }

  private void gc(long expired) {
    for (Iterator<Deque<Long>> it = data.values().iterator(); it.hasNext(); ) {
      Deque<Long> hits = it.next();
      trim(hits, expired);
      if (hits.isEmpty()) it.remove();
    }
  }

  /** Hits are appended in time order, so the expired ones are at the head. */
  private static void trim(Deque<Long> hits, long expired) {
    while (!hits.isEmpty() && hits.peekFirst() < expired) hits.removeFirst();
  }

  public static class RateExceededException extends RuntimeException {
    private final long retryAfterMillis;

    public RateExceededException(long retryAfterMillis) {
      super("Rate exceeded, retry in " + retryAfterMillis + " ms");
      this.retryAfterMillis = retryAfterMillis;
    }

    /** Until the oldest hit in the window expires, freeing one slot. */
    public long getRetryAfterMillis() {
      return retryAfterMillis;
    }
  }
}
