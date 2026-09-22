package net.pieroxy.mom.learning;

import net.pieroxy.mom.utils.mail.ImapMailboxConnection;
import org.junit.Test;

import static org.junit.Assert.assertFalse;

public class RuleLearnerSecondRunTest extends AbstractRuleLearnerTest {
  @Test
  public void secondRunWithNoNewExamplesLearnsNothing() throws Exception {
    fixture.appendMessage(messageFrom("sender@newsletter.example.com"), "mom-rules", "FROM_DOMAIN_EQUALS", "MOVE_TO", "Spam");

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      RuleLearner learner = new RuleLearner(mailbox, store());
      learner.ensureFolderSkeleton();
      learner.learnFromExamples();
    }

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      RuleLearner learner = new RuleLearner(mailbox, store());
      assertFalse(learner.learnFromExamples());
    }
  }
}
