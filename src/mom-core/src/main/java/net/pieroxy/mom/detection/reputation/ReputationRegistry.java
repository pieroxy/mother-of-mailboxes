package net.pieroxy.mom.detection.reputation;

import net.pieroxy.mom.config.general.ReputationListConfig;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Access point shared by the whole process (not per account, unlike the classifier) to the
 * configured reputation lists. A list is downloaded and refreshed as soon as it's present in
 * {@code reputationLists}, regardless of whether any matcher references it: simpler to reason
 * about ("configured means up to date"), and it lets a list be preloaded before a rule is wired
 * to it. Never a query per message — everything is in memory, see
 * {@link IpReputationList}/{@link DomainReputationList}.
 * <p>
 * Used in two basic ways: built (with its disk cache preloaded) right away at process startup so
 * a signal is available from the very first message, then {@link #start()} launches the periodic
 * background refresh. See {@code net.pieroxy.mom.standalone.Runner} and
 * {@link ReputationRegistryHolder} (how matchers, built without context, find the instance).
 */
public final class ReputationRegistry {
  private static final Logger LOGGER = Logger.getLogger(ReputationRegistry.class.getName());
  /** First retry after a failed refresh; doubles on each further failure, capped at refreshHours. */
  static final long FIRST_RETRY_DELAY_MS = TimeUnit.MINUTES.toMillis(5);

  private final Map<String, ReputationListConfig> configsById;
  private final ConcurrentHashMap<String, ReputationList> listsById = new ConcurrentHashMap<>();
  // Populated alongside listsById, from the same successful parse (startup cache load or
  // refresh) — see ReputationListsApi, which surfaces these for the homepage dashboard. Kept
  // here rather than derived on demand: re-parsing the cached content on every API call just to
  // count entries would be wasteful.
  private final ConcurrentHashMap<String, Integer> itemCountById = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Long> contentSizeBytesById = new ConcurrentHashMap<>();
  private final ReputationListStore store;
  // Filled by start(): without it, refreshNow() still downloads but schedules nothing.
  private final ConcurrentHashMap<String, Schedule> schedulesById = new ConcurrentHashMap<>();
  private final long firstRetryDelayMs;
  private volatile ScheduledExecutorService scheduler;

  public ReputationRegistry(List<ReputationListConfig> lists, String dataFolder) {
    this(lists, dataFolder, FIRST_RETRY_DELAY_MS);
  }

  /** Visible for tests: a shorter first retry delay. */
  ReputationRegistry(List<ReputationListConfig> lists, String dataFolder, long firstRetryDelayMs) {
    this.firstRetryDelayMs = firstRetryDelayMs;
    Map<String, ReputationListConfig> configs = new HashMap<>();
    if (lists != null) {
      for (ReputationListConfig cfg : lists) {
        configs.put(cfg.getId(), cfg);
      }
    }
    this.configsById = Map.copyOf(configs);
    this.store = configsById.isEmpty() ? null : new ReputationListStore(dataFolder);

    for (ReputationListConfig cfg : configsById.values()) {
      long start = System.nanoTime();
      String cached = store.load(cfg.getId());
      if (cached == null) continue;
      try {
        ReputationListParser.ParseResult result = ReputationListParser.parse(cfg.getId(), cfg.getType(), cached);
        listsById.put(cfg.getId(), result.list());
        itemCountById.put(cfg.getId(), result.validCount());
        contentSizeBytesById.put(cfg.getId(), (long) cached.getBytes(StandardCharsets.UTF_8).length);
        LOGGER.info("Reputation list [" + cfg.getId() + "]: loaded " + result.validCount() + " valid / "
            + result.invalidCount() + " invalid entries from disk cache in " + elapsedMs(start) + "ms");
      } catch (Exception e) {
        LOGGER.log(Level.WARNING, "Reputation list [" + cfg.getId() + "]: could not parse cached copy", e);
      }
    }
  }

  /** A registry with no list configured: ipScore/domainScore always return empty. */
  public static ReputationRegistry empty() {
    return new ReputationRegistry(List.of(), null);
  }

  /** Every list declared in the global config, id + type only — see {@code ReputationListsApi}. */
  public List<ReputationListConfig> getConfiguredLists() {
    return List.copyOf(configsById.values());
  }

  /** Epoch millis the cache for this id was last written, or 0 if it has none yet — see {@code ReputationListsApi}. */
  public long getLastModified(String id) {
    return store == null ? 0 : store.lastModified(id);
  }

  /** Entries successfully parsed from the currently loaded list, or 0 if none is loaded yet. */
  public int getItemCount(String id) {
    return itemCountById.getOrDefault(id, 0);
  }

  /** Byte size of the currently loaded list's raw (downloaded, pre-compression) content, or 0 if none is loaded yet. */
  public long getContentSizeBytes(String id) {
    return contentSizeBytesById.getOrDefault(id, 0L);
  }

  /**
   * Starts the periodic refresh (one task per list, on its own refreshHours). A failed refresh is
   * retried after {@link #FIRST_RETRY_DELAY_MS}, then twice as long after each further failure, up
   * to refreshHours. No effect if no list is configured, or already started.
   * <p>
   * The first download of each list is deferred until its disk cache is actually due for a
   * refresh (file age &ge; refreshHours), rather than always firing on process startup:
   * otherwise, a service stuck in a restart loop (crash loop, bad config...) would re-download on
   * every restart, potentially far faster than refreshHours, until it gets banned by the remote
   * source — exactly what refreshHours is meant to prevent.
   */
  public synchronized void start() {
    if (configsById.isEmpty() || scheduler != null) return;
    scheduler = Executors.newScheduledThreadPool(1, r -> {
      Thread t = new Thread(r, "reputation-refresh");
      t.setDaemon(true);
      return t;
    });
    long now = System.currentTimeMillis();
    for (ReputationListConfig cfg : configsById.values()) {
      Schedule schedule = new Schedule(cfg, firstRetryDelayMs);
      long initialDelayMs = initialDelayMs(store.lastModified(cfg.getId()), now, schedule.refreshMs);
      if (initialDelayMs > 0) {
        LOGGER.info("Reputation list [" + cfg.getId() + "]: cached copy is " + formatDuration(schedule.refreshMs - initialDelayMs)
            + " old, next download in " + formatDuration(initialDelayMs));
      }
      schedulesById.put(cfg.getId(), schedule);
      synchronized (schedule) {
        scheduleNext(schedule, initialDelayMs);
      }
    }
  }

  /**
   * Downloads a list right away, on the calling thread. On success, the next scheduled refresh is
   * pushed back to refreshHours from now; on failure, the schedule is left as is.
   *
   * @throws IllegalArgumentException if no list has this id.
   * @throws Exception why the download failed; the last known copy stays in use.
   */
  public void refreshNow(String id) throws Exception {
    ReputationListConfig cfg = configsById.get(id);
    if (cfg == null) throw new IllegalArgumentException("Unknown reputation list: " + id);
    LOGGER.info("Reputation list [" + id + "]: refresh requested by hand");
    Schedule schedule = schedulesById.get(id);
    if (schedule == null) {
      refreshByHand(cfg);
      return;
    }
    synchronized (schedule) {
      refreshByHand(cfg);
      schedule.retryDelayMs = firstRetryDelayMs;
      scheduleNext(schedule, schedule.refreshMs);
    }
  }

  private void refreshByHand(ReputationListConfig cfg) throws Exception {
    try {
      refresh(cfg);
    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Reputation list [" + cfg.getId() + "]: refresh by hand failed, keeping last known copy", e);
      throw e;
    }
  }

  private void runScheduled(Schedule schedule, long generation) {
    synchronized (schedule) {
      // A refreshNow() rescheduled this list while this run was waiting for the lock.
      if (generation != schedule.generation) return;
      long nextDelayMs;
      try {
        refresh(schedule.cfg);
        schedule.retryDelayMs = firstRetryDelayMs;
        nextDelayMs = schedule.refreshMs;
      } catch (Exception e) {
        nextDelayMs = Math.min(schedule.retryDelayMs, schedule.refreshMs);
        schedule.retryDelayMs = Math.min(schedule.retryDelayMs * 2, schedule.refreshMs);
        // A failed refresh must never wipe out an existing signal: the last version that worked
        // (disk cache loaded at startup, or a previous success) stays in use.
        LOGGER.log(Level.WARNING, "Reputation list [" + schedule.cfg.getId() + "]: refresh failed, keeping last known copy."
            + " Next attempt in " + formatDuration(nextDelayMs), e);
      }
      scheduleNext(schedule, nextDelayMs);
    }
  }

  /** Must be called with {@code schedule}'s lock held. Replaces any pending run. */
  private void scheduleNext(Schedule schedule, long delayMs) {
    ScheduledExecutorService current = scheduler;
    if (current == null) return; // stopped
    long generation = ++schedule.generation;
    try {
      current.schedule(() -> runScheduled(schedule, generation), delayMs, TimeUnit.MILLISECONDS);
    } catch (RejectedExecutionException e) {
      // stop() raced this: nothing left to schedule.
    }
  }

  /** One list's refresh state; fields guarded by the instance's own lock. */
  private static final class Schedule {
    final ReputationListConfig cfg;
    final long refreshMs;
    long retryDelayMs;
    // Bumped by each scheduleNext(): a pending run whose generation is stale does nothing.
    long generation;

    Schedule(ReputationListConfig cfg, long firstRetryDelayMs) {
      this.cfg = cfg;
      this.refreshMs = TimeUnit.HOURS.toMillis(Math.max(1, cfg.getRefreshHours()));
      this.retryDelayMs = firstRetryDelayMs;
    }
  }

  /** @return the delay before the next download is due: 0 if there's no cache, or if its age already exceeds refreshMs. */
  static long initialDelayMs(long cacheLastModifiedMillis, long nowMillis, long refreshMs) {
    if (cacheLastModifiedMillis <= 0) return 0;
    long age = nowMillis - cacheLastModifiedMillis;
    return age >= refreshMs ? 0 : refreshMs - age;
  }

  private static String formatDuration(long ms) {
    long minutes = TimeUnit.MILLISECONDS.toMinutes(ms);
    return minutes < 60 ? minutes + "min" : TimeUnit.MILLISECONDS.toHours(ms) + "h";
  }

  public synchronized void stop() {
    if (scheduler != null) {
      scheduler.shutdownNow();
      scheduler = null;
      schedulesById.clear();
    }
  }

  private void refresh(ReputationListConfig cfg) throws Exception {
    long overallStart = System.nanoTime();
    long fetchStart = System.nanoTime();
    String content = ReputationListFetcher.fetch(cfg.getUrl());
    long fetchMs = elapsedMs(fetchStart);

    long parseStart = System.nanoTime();
    ReputationListParser.ParseResult result = ReputationListParser.parse(cfg.getId(), cfg.getType(), content);
    long parseMs = elapsedMs(parseStart);

    listsById.put(cfg.getId(), result.list());
    store.save(cfg.getId(), content);
    itemCountById.put(cfg.getId(), result.validCount());
    contentSizeBytesById.put(cfg.getId(), (long) content.getBytes(StandardCharsets.UTF_8).length);

    LOGGER.info("Reputation list [" + cfg.getId() + "] refreshed from " + cfg.getUrl() + ": "
        + result.validCount() + " valid / " + result.invalidCount() + " invalid entries "
        + "(fetch " + fetchMs + "ms, parse " + parseMs + "ms, total " + elapsedMs(overallStart) + "ms)");
  }

  private static long elapsedMs(long startNanos) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
  }

  /** @return le pire (max) score parmi les listes IP_CIDR de listIds qui contiennent ip, et l'id de la liste correspondante ; vide si aucune ne matche. */
  public Optional<ReputationMatch> ipScore(String ip, Set<String> listIds) {
    return match(ReputationListType.IP_CIDR, ip, listIds);
  }

  /** @return le pire (max) score parmi les listes DOMAIN de listIds qui contiennent domain, et l'id de la liste correspondante ; vide si aucune ne matche. */
  public Optional<ReputationMatch> domainScore(String domain, Set<String> listIds) {
    return match(ReputationListType.DOMAIN, domain, listIds);
  }

  private Optional<ReputationMatch> match(ReputationListType expectedType, String value, Set<String> listIds) {
    String bestId = null;
    double best = Double.NaN;
    for (String id : listIds) {
      ReputationListConfig cfg = configsById.get(id);
      if (cfg == null) {
        LOGGER.warning("Reputation list [" + id + "] referenced by a matcher but not declared in reputationLists");
        continue;
      }
      if (cfg.getType() != expectedType) {
        LOGGER.warning("Reputation list [" + id + "] is of type " + cfg.getType() + ", not usable from this matcher");
        continue;
      }
      ReputationList list = listsById.get(id);
      if (list == null || !list.contains(value)) continue;
      if (Double.isNaN(best) || cfg.getScore() > best) {
        best = cfg.getScore();
        bestId = id;
      }
    }
    return Double.isNaN(best) ? Optional.empty() : Optional.of(new ReputationMatch(bestId, best));
  }
}
