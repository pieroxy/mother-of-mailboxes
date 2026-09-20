package net.pieroxy.imf.detection.reputation;

import net.pieroxy.imf.config.general.ReputationListConfig;
import net.pieroxy.imf.detection.reputation.ReputationListStore;
import net.pieroxy.imf.detection.reputation.ReputationListType;
import net.pieroxy.imf.detection.reputation.ReputationMatch;
import net.pieroxy.imf.detection.reputation.ReputationRegistry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReputationRegistryTest {

  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  private ReputationListConfig list(String id, ReputationListType type, double score) {
    ReputationListConfig cfg = new ReputationListConfig();
    cfg.setId(id);
    cfg.setType(type);
    cfg.setUrl("file:///unused-in-this-test");
    cfg.setRefreshHours(24);
    cfg.setScore(score);
    return cfg;
  }

  @Test
  public void loadsFromDiskCacheAtConstructionTimeWithoutNetwork() throws Exception {
    String dataFolder = tempFolder.getRoot().getAbsolutePath();
    new ReputationListStore(dataFolder).save("blocklist", "1.2.3.0/24\n");

    ReputationRegistry registry = new ReputationRegistry(
        List.of(list("blocklist", ReputationListType.IP_CIDR, 1.0)), dataFolder);

    Optional<ReputationMatch> match = registry.ipScore("1.2.3.4", Set.of("blocklist"));
    assertTrue(match.isPresent());
    assertEquals(1.0, match.get().score(), 0.0001);
    assertEquals("blocklist", match.get().listId());
  }

  @Test
  public void unmatchedValueYieldsEmptyScore() throws Exception {
    String dataFolder = tempFolder.getRoot().getAbsolutePath();
    new ReputationListStore(dataFolder).save("blocklist", "1.2.3.0/24\n");

    ReputationRegistry registry = new ReputationRegistry(
        List.of(list("blocklist", ReputationListType.IP_CIDR, 1.0)), dataFolder);

    assertFalse(registry.ipScore("9.9.9.9", Set.of("blocklist")).isPresent());
  }

  @Test
  public void worstScoreWinsAcrossMultipleReferencedLists() throws Exception {
    String dataFolder = tempFolder.getRoot().getAbsolutePath();
    ReputationListStore store = new ReputationListStore(dataFolder);
    store.save("mild", "1.2.3.0/24\n");
    store.save("severe", "1.2.3.0/24\n");

    ReputationRegistry registry = new ReputationRegistry(List.of(
        list("mild", ReputationListType.IP_CIDR, 0.3),
        list("severe", ReputationListType.IP_CIDR, 0.9)), dataFolder);

    Optional<ReputationMatch> match = registry.ipScore("1.2.3.4", Set.of("mild", "severe"));
    assertEquals(0.9, match.get().score(), 0.0001);
    assertEquals("severe", match.get().listId());
  }

  @Test
  public void unknownListIdIsIgnoredRatherThanFailing() throws Exception {
    String dataFolder = tempFolder.getRoot().getAbsolutePath();
    new ReputationListStore(dataFolder).save("blocklist", "1.2.3.0/24\n");

    ReputationRegistry registry = new ReputationRegistry(
        List.of(list("blocklist", ReputationListType.IP_CIDR, 1.0)), dataFolder);

    assertFalse(registry.ipScore("1.2.3.4", Set.of("does-not-exist")).isPresent());
  }

  @Test
  public void wrongTypeReferenceIsIgnoredRatherThanFailing() throws Exception {
    String dataFolder = tempFolder.getRoot().getAbsolutePath();
    new ReputationListStore(dataFolder).save("domain-list", "bad.example.com\n");

    ReputationRegistry registry = new ReputationRegistry(
        List.of(list("domain-list", ReputationListType.DOMAIN, 1.0)), dataFolder);

    // domain-list is of type DOMAIN: referencing it from ipScore() must never blow up.
    assertFalse(registry.ipScore("1.2.3.4", Set.of("domain-list")).isPresent());
  }

  @Test
  public void domainScoreMatchesExactDomainCaseInsensitively() throws Exception {
    String dataFolder = tempFolder.getRoot().getAbsolutePath();
    new ReputationListStore(dataFolder).save("domain-list", "bad.example.com\n");

    ReputationRegistry registry = new ReputationRegistry(
        List.of(list("domain-list", ReputationListType.DOMAIN, 0.8)), dataFolder);

    Optional<ReputationMatch> match = registry.domainScore("Bad.Example.com", Set.of("domain-list"));
    assertEquals(0.8, match.get().score(), 0.0001);
    assertEquals("domain-list", match.get().listId());
  }

  @Test
  public void emptyRegistryAlwaysYieldsEmptyScore() {
    ReputationRegistry registry = ReputationRegistry.empty();
    assertFalse(registry.ipScore("1.2.3.4", Set.of("anything")).isPresent());
  }

  @Test
  public void noPriorCacheYieldsEmptyScoreUntilFirstRefresh() {
    String dataFolder = tempFolder.getRoot().getAbsolutePath();
    ReputationRegistry registry = new ReputationRegistry(
        List.of(list("blocklist", ReputationListType.IP_CIDR, 1.0)), dataFolder);

    assertFalse(registry.ipScore("1.2.3.4", Set.of("blocklist")).isPresent());
  }

  // --- initialDelayMs(...): what keeps a service stuck in a crash loop from re-downloading on
  // every restart (see start()) ---

  @Test
  public void noCacheAtAllMeansDownloadRightAway() {
    assertEquals(0L, ReputationRegistry.initialDelayMs(0, System.currentTimeMillis(), TimeUnit.HOURS.toMillis(24)));
  }

  @Test
  public void freshCacheDefersTheFirstDownload() {
    long now = System.currentTimeMillis();
    long oneHourAgo = now - TimeUnit.HOURS.toMillis(1);
    long refreshMs = TimeUnit.HOURS.toMillis(24);

    long delay = ReputationRegistry.initialDelayMs(oneHourAgo, now, refreshMs);

    assertEquals(refreshMs - TimeUnit.HOURS.toMillis(1), delay);
  }

  @Test
  public void staleCacheMeansDownloadRightAway() {
    long now = System.currentTimeMillis();
    long thirtyHoursAgo = now - TimeUnit.HOURS.toMillis(30);

    assertEquals(0L, ReputationRegistry.initialDelayMs(thirtyHoursAgo, now, TimeUnit.HOURS.toMillis(24)));
  }

  // --- start() end to end: the behavior described above really holds on a real registry, not
  // just in the isolated calculation ---

  @Test
  public void startDoesNotRedownloadAFreshCache() throws Exception {
    String dataFolder = tempFolder.getRoot().getAbsolutePath();
    File sourceFile = tempFolder.newFile("source.txt");
    Files.writeString(sourceFile.toPath(), "9.9.9.0/24\n"); // different from the cache: proves it isn't re-read

    new ReputationListStore(dataFolder).save("blocklist", "1.2.3.0/24\n"); // brand new cache (mtime = now)

    ReputationListConfig cfg = list("blocklist", ReputationListType.IP_CIDR, 1.0);
    cfg.setUrl(sourceFile.toURI().toString());
    cfg.setRefreshHours(24);

    ReputationRegistry registry = new ReputationRegistry(List.of(cfg), dataFolder);
    registry.start();
    try {
      Thread.sleep(300);
      assertTrue("the fresh cache must still be used", registry.ipScore("1.2.3.4", Set.of("blocklist")).isPresent());
      assertFalse("the source must not have been re-downloaded", registry.ipScore("9.9.9.9", Set.of("blocklist")).isPresent());
    } finally {
      registry.stop();
    }
  }

  @Test
  public void startRedownloadsAStaleCacheRightAway() throws Exception {
    String dataFolder = tempFolder.getRoot().getAbsolutePath();
    File sourceFile = tempFolder.newFile("source.txt");
    Files.writeString(sourceFile.toPath(), "9.9.9.0/24\n");

    ReputationListStore store = new ReputationListStore(dataFolder);
    store.save("blocklist", "1.2.3.0/24\n");
    File cacheFile = new File(new File(dataFolder, "reputation"), "blocklist.txt.lz4");
    assertTrue(cacheFile.setLastModified(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(30)));

    ReputationListConfig cfg = list("blocklist", ReputationListType.IP_CIDR, 1.0);
    cfg.setUrl(sourceFile.toURI().toString());
    cfg.setRefreshHours(24);

    ReputationRegistry registry = new ReputationRegistry(List.of(cfg), dataFolder);
    registry.start();
    try {
      Thread.sleep(300);
      assertTrue("a stale cache must be re-downloaded right at startup",
          registry.ipScore("9.9.9.9", Set.of("blocklist")).isPresent());
    } finally {
      registry.stop();
    }
  }
}
