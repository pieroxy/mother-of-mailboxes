package net.pieroxy.mom.rules;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class MailAccountStateStoreTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  @Test
  public void loadReturnsFreshStateWhenFileDoesNotExist() {
    MailAccountStateStore store = new MailAccountStateStore(tmp.getRoot().getAbsolutePath(), "some-account");

    MailAccountState state = store.load();

    assertNotNull(state);
    assertEquals(0, state.getUidValidity());
    assertEquals(0, state.getLastUid());
  }

  @Test
  public void saveThenLoadRoundTrips() {
    MailAccountStateStore store = new MailAccountStateStore(tmp.getRoot().getAbsolutePath(), "some-account");
    MailAccountState state = new MailAccountState();
    state.setUidValidity(42);
    state.setLastUid(1337);

    store.save(state);
    MailAccountState reloaded = store.load();

    assertEquals(42, reloaded.getUidValidity());
    assertEquals(1337, reloaded.getLastUid());
  }

  @Test
  public void twoAccountsDoNotShareTheSameFile() {
    MailAccountStateStore storeA = new MailAccountStateStore(tmp.getRoot().getAbsolutePath(), "account-a");
    MailAccountStateStore storeB = new MailAccountStateStore(tmp.getRoot().getAbsolutePath(), "account-b");

    MailAccountState stateA = new MailAccountState();
    stateA.setLastUid(1);
    storeA.save(stateA);

    assertEquals(0, storeB.load().getLastUid());
    assertEquals(1, storeA.load().getLastUid());
  }

  @Test
  public void loadReturnsFreshStateWhenFileIsCorrupted() throws IOException {
    File f = new File(tmp.getRoot(), "broken-account.json");
    try (FileWriter w = new FileWriter(f)) {
      w.write("{ this is not valid json");
    }
    MailAccountStateStore store = new MailAccountStateStore(tmp.getRoot().getAbsolutePath(), "broken-account");

    MailAccountState state = store.load();

    assertNotNull(state);
    assertEquals(0, state.getUidValidity());
  }

  @Test
  public void saveCreatesDataFolderIfMissing() {
    File nested = new File(tmp.getRoot(), "does/not/exist/yet");
    MailAccountStateStore store = new MailAccountStateStore(nested.getAbsolutePath(), "some-account");

    store.save(new MailAccountState());

    assertEquals(true, new File(nested, "some-account.json").exists());
  }
}
