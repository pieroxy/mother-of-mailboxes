package net.pieroxy.imf.learning;

import net.pieroxy.imf.utils.mail.ImapMailboxConnection;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class RuleLearnerEnsureFolderSkeletonTest extends AbstractRuleLearnerTest {
  @Test
  public void ensureFolderSkeletonCreatesTheLearnableTree() throws Exception {
    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      new RuleLearner(mailbox, store()).ensureFolderSkeleton();

      assertTrue(mailbox.getOrCreateFolder("imf-rules", "FROM_DOMAIN_EQUALS", "MOVE_TO").exists());
      assertTrue(mailbox.getOrCreateFolder("imf-rules", "Done").exists());
    }
  }
}
