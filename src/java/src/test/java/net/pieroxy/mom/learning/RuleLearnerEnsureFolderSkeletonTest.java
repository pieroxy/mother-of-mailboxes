package net.pieroxy.mom.learning;

import net.pieroxy.mom.utils.mail.ImapMailboxConnection;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class RuleLearnerEnsureFolderSkeletonTest extends AbstractRuleLearnerTest {
  @Test
  public void ensureFolderSkeletonCreatesTheLearnableTree() throws Exception {
    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      new RuleLearner(mailbox, store()).ensureFolderSkeleton();

      assertTrue(mailbox.getOrCreateFolder("mom-rules", "FROM_DOMAIN_EQUALS", "MOVE_TO").exists());
      assertTrue(mailbox.getOrCreateFolder("mom-rules", "Done").exists());
    }
  }
}
