package net.pieroxy.imf.detection.reputation;

import net.pieroxy.imf.detection.reputation.ReputationListStore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ReputationListStoreTest {

  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  @Test
  public void missingCacheReturnsNull() {
    ReputationListStore store = new ReputationListStore(tempFolder.getRoot().getAbsolutePath());
    assertNull(store.load("unknown-id"));
  }

  @Test
  public void savedContentRoundTripsThroughLz4() throws Exception {
    ReputationListStore store = new ReputationListStore(tempFolder.getRoot().getAbsolutePath());
    store.save("spamhaus-drop", "1.2.3.0/24\n5.6.7.8\n");
    assertEquals("1.2.3.0/24\n5.6.7.8\n", store.load("spamhaus-drop"));
  }

  @Test
  public void savingAgainOverwritesThePreviousCache() throws Exception {
    ReputationListStore store = new ReputationListStore(tempFolder.getRoot().getAbsolutePath());
    store.save("my-list", "1.2.3.4\n");
    store.save("my-list", "5.6.7.8\n");
    assertEquals("5.6.7.8\n", store.load("my-list"));
  }

  @Test
  public void lastModifiedIsZeroWhenNoCacheExists() {
    ReputationListStore store = new ReputationListStore(tempFolder.getRoot().getAbsolutePath());
    assertEquals(0L, store.lastModified("unknown-id"));
  }

  @Test
  public void lastModifiedReflectsTheMostRecentSave() throws Exception {
    ReputationListStore store = new ReputationListStore(tempFolder.getRoot().getAbsolutePath());
    long before = System.currentTimeMillis();
    store.save("my-list", "1.2.3.4\n");
    long after = System.currentTimeMillis();

    long lastModified = store.lastModified("my-list");
    assertTrue(lastModified >= before - 1000 && lastModified <= after + 1000);
  }
}
