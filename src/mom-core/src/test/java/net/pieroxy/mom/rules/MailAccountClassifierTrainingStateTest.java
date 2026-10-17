package net.pieroxy.mom.rules;

import net.pieroxy.mom.detection.classifier.ClassifierCorpusStore;
import org.junit.Test;

import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Checks that {@link MailAccount}'s classifier training getters (see {@code AccountsApi}) read
 * from the exact same account-scoped {@link ClassifierCorpusStore} paths the trainers write to —
 * not a separate {@code ClassifierCorpusStore} instance/test of its own (see
 * {@code ClassifierCorpusStoreTest}/{@code SubjectClassifierTrainerTest} for that).
 */
public class MailAccountClassifierTrainingStateTest extends AbstractMailAccountTest {

  @Test
  public void reportsUntrainedWithZeroCountsByDefault() {
    MailAccount account = accountWith();

    assertFalse(account.isSubjectClassifierTrained());
    assertFalse(account.isHeaderClassifierTrained());
    assertFalse(account.isBodyClassifierTrained());
    assertNull(account.getSubjectClassifierTrainedTimestamp());
    assertNull(account.getHeaderClassifierTrainedTimestamp());
    assertNull(account.getBodyClassifierTrainedTimestamp());
    assertEquals(0, account.getClassifierTrainingCounts().getSpamCount());
    assertEquals(0, account.getClassifierTrainingCounts().getHamCount());
  }

  @Test
  public void reportsTrainedAndTheSavedCountsOnceOnDisk() throws Exception {
    MailAccount account = accountWith();
    // Same dataFolder/accountKey as accountWith()'s "test-account" — see GreenMailImapFixture.
    ClassifierCorpusStore store = new ClassifierCorpusStore(tempFolder.getRoot().getAbsolutePath(), "test-account", 30);
    store.saveTrainingCounts(60, 55);
    Files.write(store.getModelFile().toPath(), new byte[]{1, 2, 3});

    assertTrue(account.isSubjectClassifierTrained());
    assertFalse("only the subject model was written", account.isHeaderClassifierTrained());
    assertNotNull(account.getSubjectClassifierTrainedTimestamp());
    assertEquals(60, account.getClassifierTrainingCounts().getSpamCount());
    assertEquals(55, account.getClassifierTrainingCounts().getHamCount());
  }
}
