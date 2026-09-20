package net.pieroxy.imf.learning;

import net.pieroxy.imf.config.general.LearningShortcutConfiguration;
import net.pieroxy.imf.config.general.MailFilterRuleActionConfiguration;
import net.pieroxy.imf.config.general.MailFilterRuleMatcherConfiguration;
import net.pieroxy.imf.utils.mail.ImapMailboxConnection;
import net.pieroxy.imf.rules.actions.ActionType;
import net.pieroxy.imf.rules.matchers.MatcherType;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.fail;

public class RuleLearnerShortcutValidationTest extends AbstractRuleLearnerTest {

  private static LearningShortcutConfiguration shortcut(String name, MatcherType matcherType, ActionType actionType, String actionKey) {
    LearningShortcutConfiguration shortcut = new LearningShortcutConfiguration();
    shortcut.setName(name);

    MailFilterRuleMatcherConfiguration matcher = new MailFilterRuleMatcherConfiguration();
    matcher.setType(matcherType);
    shortcut.setMatcher(matcher);

    MailFilterRuleActionConfiguration action = new MailFilterRuleActionConfiguration();
    action.setType(actionType);
    action.setKey(actionKey);
    shortcut.setAction(action);

    return shortcut;
  }

  private void expectRejected(LearningShortcutConfiguration shortcut) throws Exception {
    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      new RuleLearner(mailbox, store(), List.of(shortcut));
      fail("should have thrown");
    } catch (IllegalArgumentException expected) {
      // ok
    }
  }

  @Test
  public void acceptsAWellFormedShortcut() throws Exception {
    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      new RuleLearner(mailbox, store(),
              List.of(shortcut("MoveSameDomainToSpam", MatcherType.FROM_DOMAIN_EQUALS, ActionType.MOVE_TO_AND_READ, "Spam")));
      // must not throw
    }
  }

  @Test
  public void rejectsABlankName() throws Exception {
    expectRejected(shortcut("", MatcherType.FROM_DOMAIN_EQUALS, ActionType.MOVE_TO, "Spam"));
  }

  @Test
  public void rejectsTwoShortcutsWithTheSameName() throws Exception {
    LearningShortcutConfiguration a = shortcut("Same", MatcherType.FROM_DOMAIN_EQUALS, ActionType.MOVE_TO, "Spam");
    LearningShortcutConfiguration b = shortcut("Same", MatcherType.FROM_EQUALS, ActionType.MOVE_TO, "Archive");
    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      new RuleLearner(mailbox, store(), List.of(a, b));
      fail("should have thrown");
    } catch (IllegalArgumentException expected) {
      // ok
    }
  }

  @Test
  public void rejectsANameCollidingWithAMatcherTypeName() throws Exception {
    expectRejected(shortcut("FROM_DOMAIN_EQUALS", MatcherType.FROM_EQUALS, ActionType.MOVE_TO, "Spam"));
  }

  @Test
  public void rejectsANameCollidingWithTheDoneFolder() throws Exception {
    expectRejected(shortcut("Done", MatcherType.FROM_DOMAIN_EQUALS, ActionType.MOVE_TO, "Spam"));
  }

  @Test
  public void rejectsAnUnlearnableMatcherType() throws Exception {
    expectRejected(shortcut("Shortcut", MatcherType.SPF_RESULT_EQUALS, ActionType.MOVE_TO, "Spam"));
  }

  @Test
  public void rejectsAnUnlearnableActionType() throws Exception {
    expectRejected(shortcut("Shortcut", MatcherType.FROM_DOMAIN_EQUALS, ActionType.NOOP, null));
  }

  @Test
  public void rejectsAMatcherWithAKeySet() throws Exception {
    LearningShortcutConfiguration shortcut = shortcut("Shortcut", MatcherType.FROM_DOMAIN_EQUALS, ActionType.MOVE_TO, "Spam");
    shortcut.getMatcher().setKey("example.com");
    expectRejected(shortcut);
  }

  @Test
  public void rejectsAMatcherWithKeysSet() throws Exception {
    LearningShortcutConfiguration shortcut = shortcut("Shortcut", MatcherType.FROM_DOMAIN_EQUALS, ActionType.MOVE_TO, "Spam");
    shortcut.getMatcher().setKeys(Set.of("example.com"));
    expectRejected(shortcut);
  }

  @Test
  public void rejectsAMissingActionKey() throws Exception {
    expectRejected(shortcut("Shortcut", MatcherType.FROM_DOMAIN_EQUALS, ActionType.MOVE_TO, null));
  }
}
