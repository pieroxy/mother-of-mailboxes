package net.pieroxy.imf.detection.reputation;

import net.pieroxy.imf.config.general.ReputationListConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
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
 * background refresh. See {@code net.pieroxy.imf.standalone.Runner} and
 * {@link ReputationRegistryHolder} (how matchers, built without context, find the instance).
 */
public final class ReputationRegistry {
  private static final Logger LOGGER = Logger.getLogger(ReputationRegistry.class.getName());

  private final Map<String, ReputationListConfig> configsById;
  private final ConcurrentHashMap<String, ReputationList> listsById = new ConcurrentHashMap<>();
  private final ReputationListStore store;
  private ScheduledExecutorService scheduler;

  public ReputationRegistry(List<ReputationListConfig> lists, String dataFolder) {
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

  /**
   * Starts the periodic refresh (one task per list, on its own refreshHours). No effect if no
   * list is configured, or already started.
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
      long refreshMs = TimeUnit.HOURS.toMillis(Math.max(1, cfg.getRefreshHours()));
      long initialDelayMs = initialDelayMs(store.lastModified(cfg.getId()), now, refreshMs);
      if (initialDelayMs > 0) {
        LOGGER.info("Reputation list [" + cfg.getId() + "]: cached copy is " + formatDuration(refreshMs - initialDelayMs)
            + " old, next download in " + formatDuration(initialDelayMs));
      }
      scheduler.scheduleAtFixedRate(() -> refresh(cfg), initialDelayMs, refreshMs, TimeUnit.MILLISECONDS);
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
    }
  }

  private void refresh(ReputationListConfig cfg) {
    long overallStart = System.nanoTime();
    try {
      long fetchStart = System.nanoTime();
      String content = ReputationListFetcher.fetch(cfg.getUrl());
      long fetchMs = elapsedMs(fetchStart);

      long parseStart = System.nanoTime();
      ReputationListParser.ParseResult result = ReputationListParser.parse(cfg.getId(), cfg.getType(), content);
      long parseMs = elapsedMs(parseStart);

      listsById.put(cfg.getId(), result.list());
      store.save(cfg.getId(), content);

      LOGGER.info("Reputation list [" + cfg.getId() + "] refreshed from " + cfg.getUrl() + ": "
          + result.validCount() + " valid / " + result.invalidCount() + " invalid entries "
          + "(fetch " + fetchMs + "ms, parse " + parseMs + "ms, total " + elapsedMs(overallStart) + "ms)");
    } catch (Exception e) {
      // Keep the last version that worked (disk cache already loaded at startup, or a previous
      // in-memory success): a failed refresh must never wipe out an existing signal.
      LOGGER.log(Level.WARNING, "Reputation list [" + cfg.getId() + "]: refresh failed after "
          + elapsedMs(overallStart) + "ms, keeping last known copy", e);
    }
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
