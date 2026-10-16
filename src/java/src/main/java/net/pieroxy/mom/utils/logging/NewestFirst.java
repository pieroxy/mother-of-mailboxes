package net.pieroxy.mom.utils.logging;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.function.ToLongFunction;

/**
 * Collects, from entries read oldest first, the {@code limit} newest matching ones, holding no
 * more than that in memory — the paging shared by the log readers.
 */
final class NewestFirst<E> {
  private final int limit;
  private final ToLongFunction<E> indexOf;
  private final Deque<E> kept = new ArrayDeque<>();
  private boolean olderMatches;

  NewestFirst(int limit, ToLongFunction<E> indexOf) {
    this.limit = limit;
    this.indexOf = indexOf;
  }

  /** Call for each matching entry, oldest first. */
  void add(E entry) {
    kept.addLast(entry);
    if (kept.size() > limit) {
      kept.removeFirst();
      olderMatches = true;
    }
  }

  List<E> newestFirst() {
    List<E> entries = new ArrayList<>(kept);
    Collections.reverse(entries);
    return entries;
  }

  /** The index to resume from for older entries; null when none matched before the kept ones. */
  Long nextCursor() {
    return olderMatches && !kept.isEmpty() ? indexOf.applyAsLong(kept.peekFirst()) : null;
  }
}
