package net.pieroxy.mom.utils.mail;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/** At most {@code max} sends in any rolling hour. Not thread-safe: the caller synchronizes. */
class SendRateLimit {
  private final int max;
  private final Clock clock;
  private final Deque<Instant> recentSends = new ArrayDeque<>();

  SendRateLimit(int max, Clock clock) {
    this.max = max;
    this.clock = clock;
  }

  /** @return false, without counting it, if {@code max} sends already happened in the last hour. */
  boolean tryAcquire() {
    Instant now = clock.instant();
    Instant cutoff = now.minus(Duration.ofHours(1));
    while (!recentSends.isEmpty() && !recentSends.peekFirst().isAfter(cutoff)) {
      recentSends.pollFirst();
    }
    if (recentSends.size() >= max) return false;
    recentSends.addLast(now);
    return true;
  }

  /** Gives back the last {@link #tryAcquire}, for a send that failed. */
  void release() {
    recentSends.pollLast();
  }
}
