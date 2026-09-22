package net.pieroxy.mom.learning;

import net.pieroxy.mom.config.general.LearningShortcutConfiguration;
import net.pieroxy.mom.config.general.MailFilterRuleActionConfiguration;
import net.pieroxy.mom.config.general.MailFilterRuleConfiguration;
import net.pieroxy.mom.config.general.MailFilterRuleMatcherConfiguration;
import net.pieroxy.mom.utils.mail.ImapMailboxConnection;
import net.pieroxy.mom.rules.actions.ActionType;
import net.pieroxy.mom.rules.matchers.MatcherType;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RuleLearnerShortcutTest extends AbstractRuleLearnerTest {

  private LearningShortcutConfiguration moveSameDomainToSpam() {
    LearningShortcutConfiguration shortcut = new LearningShortcutConfiguration();
    shortcut.setName("MoveSameDomainToSpam");

    MailFilterRuleMatcherConfiguration matcher = new MailFilterRuleMatcherConfiguration();
    matcher.setType(MatcherType.FROM_DOMAIN_EQUALS);
    shortcut.setMatcher(matcher);

    MailFilterRuleActionConfiguration action = new MailFilterRuleActionConfiguration();
    action.setType(ActionType.MOVE_TO_AND_READ);
    action.setKey("Spam");
    shortcut.setAction(action);

    return shortcut;
  }

  @Test
  public void ensureFolderSkeletonCreatesAFlatFolderNotANestedOne() throws Exception {
    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      new RuleLearner(mailbox, store(), List.of(moveSameDomainToSpam())).ensureFolderSkeleton();

      assertTrue(mailbox.getOrCreateFolder("mom-rules", "MoveSameDomainToSpam").exists());
    }
  }

  @Test
  public void learnsARuleFromAShortcutExampleAndRunsItsFixedAction() throws Exception {
    fixture.appendMessage(messageFrom("sender@newsletter.example.com"), "mom-rules", "MoveSameDomainToSpam");

    boolean learnedSomething;
    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      RuleLearner learner = new RuleLearner(mailbox, store(), List.of(moveSameDomainToSpam()));
      learner.ensureFolderSkeleton();
      learnedSomething = learner.learnFromExamples();
    }

    assertTrue(learnedSomething);

    List<MailFilterRuleConfiguration> learned = store().load();
    assertEquals(1, learned.size());
    assertEquals(MatcherType.FROM_DOMAIN_EQUALS, learned.get(0).getMatcher().getType());
    assertEquals("newsletter.example.com", learned.get(0).getMatcher().getKey());
    assertEquals(ActionType.MOVE_TO_AND_READ, learned.get(0).getAction().getType());
    assertEquals("Spam", learned.get(0).getAction().getKey());

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      // MOVE_TO_AND_READ already relocated the example to Spam itself, so it's not in Done either
      // (see RuleLearner.learnFromExample: Done is only a fallback for actions that don't move
      // the message, e.g. READ alone).
      assertEquals(1, mailbox.getAllMessages(mailbox.getOrCreateFolder("Spam")).length);
      assertEquals(0, mailbox.getAllMessages(mailbox.getOrCreateFolder("mom-rules", "MoveSameDomainToSpam")).length);
      assertEquals(0, mailbox.getAllMessages(mailbox.getOrCreateFolder("mom-rules", "Done")).length);
    }
  }

  @Test
  public void secondRunWithNoNewShortcutExamplesLearnsNothing() throws Exception {
    fixture.appendMessage(messageFrom("sender@newsletter.example.com"), "mom-rules", "MoveSameDomainToSpam");

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      RuleLearner learner = new RuleLearner(mailbox, store(), List.of(moveSameDomainToSpam()));
      learner.ensureFolderSkeleton();
      learner.learnFromExamples();
    }

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      RuleLearner learner = new RuleLearner(mailbox, store(), List.of(moveSameDomainToSpam()));
      assertFalse(learner.learnFromExamples());
    }
  }
}
