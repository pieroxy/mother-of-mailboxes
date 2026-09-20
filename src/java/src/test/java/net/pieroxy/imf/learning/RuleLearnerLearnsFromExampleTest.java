package net.pieroxy.imf.learning;

import net.pieroxy.imf.config.MailFilterRuleConfiguration;
import net.pieroxy.imf.utils.mail.ImapMailboxConnection;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RuleLearnerLearnsFromExampleTest extends AbstractRuleLearnerTest {
  @Test
  public void learnsARuleFromAnExampleAndRunsItsAction() throws Exception {
    fixture.appendMessage(messageFrom("sender@newsletter.example.com"), "imf-rules", "FROM_DOMAIN_EQUALS", "MOVE_TO", "Spam");

    boolean learnedSomething;
    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      RuleLearner learner = new RuleLearner(mailbox, store());
      learner.ensureFolderSkeleton();
      learnedSomething = learner.learnFromExamples();
    }

    assertTrue(learnedSomething);

    List<MailFilterRuleConfiguration> learned = store().load();
    assertEquals(1, learned.size());
    assertEquals("newsletter.example.com", learned.get(0).getMatcher().getKey());

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      assertEquals(1, mailbox.getAllMessages(mailbox.getOrCreateFolder("Spam")).length);
      assertEquals(0, mailbox.getAllMessages(mailbox.getOrCreateFolder("imf-rules", "FROM_DOMAIN_EQUALS", "MOVE_TO", "Spam")).length);
    }
  }
}
