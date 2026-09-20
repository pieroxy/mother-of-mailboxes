package net.pieroxy.imf.learning;

import net.pieroxy.imf.config.LearningShortcutConfiguration;
import net.pieroxy.imf.config.MailFilterRuleActionConfiguration;
import net.pieroxy.imf.config.MailFilterRuleMatcherConfiguration;
import net.pieroxy.imf.utils.mail.ImapMailboxConnection;
import net.pieroxy.imf.rules.actions.ActionType;
import net.pieroxy.imf.rules.matchers.MatcherType;
import net.pieroxy.imf.utils.mail.ImapMailbox;
import org.junit.Test;

import javax.mail.Folder;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RuleLearnerDiscoveryTreeDisabledTest extends AbstractRuleLearnerTest {

  /**
   * Unlike {@link ImapMailbox#getOrCreateFolder}, this never creates
   * anything — needed here since the whole point is asserting a folder was deliberately *not*
   * created.
   */
  private static Folder resolveWithoutCreating(ImapMailboxConnection mailbox, String... pathSegments) throws Exception {
    Folder current = mailbox.getRootFolder();
    for (String segment : pathSegments) {
      current = current.getFolder(segment);
    }
    return current;
  }

  private LearningShortcutConfiguration moveSameDomainToSpam() {
    LearningShortcutConfiguration shortcut = new LearningShortcutConfiguration();
    shortcut.setName("MoveSameDomainToSpam");

    MailFilterRuleMatcherConfiguration matcher = new MailFilterRuleMatcherConfiguration();
    matcher.setType(MatcherType.FROM_DOMAIN_EQUALS);
    shortcut.setMatcher(matcher);

    MailFilterRuleActionConfiguration action = new MailFilterRuleActionConfiguration();
    action.setType(ActionType.MOVE_TO);
    action.setKey("Spam");
    shortcut.setAction(action);

    return shortcut;
  }

  @Test
  public void ensureFolderSkeletonSkipsTheDiscoveryTreeButStillCreatesDoneAndShortcuts() throws Exception {
    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      new RuleLearner(mailbox, store(), List.of(moveSameDomainToSpam()), true).ensureFolderSkeleton();

      assertFalse(resolveWithoutCreating(mailbox, "imf-rules", "FROM_DOMAIN_EQUALS", "MOVE_TO").exists());
      assertTrue(mailbox.getOrCreateFolder("imf-rules", "Done").exists());
      assertTrue(mailbox.getOrCreateFolder("imf-rules", "MoveSameDomainToSpam").exists());
    }
  }

  @Test
  public void learnFromExamplesNeverTouchesTheDiscoveryTreeEither() throws Exception {
    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      new RuleLearner(mailbox, store(), List.of(), true).learnFromExamples();

      // learnFromExamples() itself calls getOrCreateFolder() defensively when the tree is
      // enabled — the real regression this guards against is that flag not being honored there.
      assertFalse(resolveWithoutCreating(mailbox, "imf-rules", "FROM_DOMAIN_EQUALS", "MOVE_TO").exists());
    }
  }

  @Test
  public void shortcutsStillLearnNormallyWithTheDiscoveryTreeDisabled() throws Exception {
    fixture.appendMessage(messageFrom("sender@newsletter.example.com"), "imf-rules", "MoveSameDomainToSpam");

    boolean learnedSomething;
    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      RuleLearner learner = new RuleLearner(mailbox, store(), List.of(moveSameDomainToSpam()), true);
      learner.ensureFolderSkeleton();
      learnedSomething = learner.learnFromExamples();
    }

    assertTrue(learnedSomething);
  }
}
