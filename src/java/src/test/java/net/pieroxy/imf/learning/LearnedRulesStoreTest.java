package net.pieroxy.imf.learning;

import net.pieroxy.imf.config.general.MailFilterRuleActionConfiguration;
import net.pieroxy.imf.config.general.MailFilterRuleConfiguration;
import net.pieroxy.imf.config.general.MailFilterRuleMatcherConfiguration;
import net.pieroxy.imf.rules.actions.ActionType;
import net.pieroxy.imf.rules.matchers.MatcherType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LearnedRulesStoreTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private static MailFilterRuleConfiguration rule(String fromKey, String targetFolder) {
    MailFilterRuleMatcherConfiguration matcher = new MailFilterRuleMatcherConfiguration();
    matcher.setType(MatcherType.FROM_EQUALS);
    matcher.setKey(fromKey);

    MailFilterRuleActionConfiguration action = new MailFilterRuleActionConfiguration();
    action.setType(ActionType.MOVE_TO);
    action.setKey(targetFolder);

    MailFilterRuleConfiguration r = new MailFilterRuleConfiguration();
    r.setMatcher(matcher);
    r.setAction(action);
    return r;
  }

  @Test
  public void loadReturnsEmptyListWhenFileDoesNotExist() {
    LearnedRulesStore store = new LearnedRulesStore(tmp.getRoot().getAbsolutePath(), "account");

    assertTrue(store.load().isEmpty());
  }

  @Test
  public void addIfAbsentPersistsNewRule() {
    LearnedRulesStore store = new LearnedRulesStore(tmp.getRoot().getAbsolutePath(), "account");

    boolean added = store.addIfAbsent(rule("alice@example.com", "Spam"));

    assertTrue(added);
    List<MailFilterRuleConfiguration> rules = store.load();
    assertEquals(1, rules.size());
    assertEquals("alice@example.com", rules.get(0).getMatcher().getKey());
    assertEquals("Spam", rules.get(0).getAction().getKey());
  }

  @Test
  public void addIfAbsentIgnoresExactDuplicate() {
    LearnedRulesStore store = new LearnedRulesStore(tmp.getRoot().getAbsolutePath(), "account");
    store.addIfAbsent(rule("alice@example.com", "Spam"));

    boolean added = store.addIfAbsent(rule("alice@example.com", "Spam"));

    assertFalse(added);
    assertEquals(1, store.load().size());
  }

  @Test
  public void addIfAbsentKeepsRulesWithDifferentTargets() {
    LearnedRulesStore store = new LearnedRulesStore(tmp.getRoot().getAbsolutePath(), "account");
    store.addIfAbsent(rule("alice@example.com", "Spam"));

    boolean added = store.addIfAbsent(rule("alice@example.com", "Archive"));

    assertTrue(added);
    assertEquals(2, store.load().size());
  }

  @Test
  public void addIfAbsentMergesDifferentMatcherKeysForTheSameActionIntoOneRule() {
    LearnedRulesStore store = new LearnedRulesStore(tmp.getRoot().getAbsolutePath(), "account");
    store.addIfAbsent(rule("alice@example.com", "Spam"));

    boolean added = store.addIfAbsent(rule("bob@example.com", "Spam"));

    assertTrue(added);
    List<MailFilterRuleConfiguration> rules = store.load();
    assertEquals("same matcher type + same action: a single rule, not two", 1, rules.size());
    MailFilterRuleMatcherConfiguration matcher = rules.get(0).getMatcher();
    assertNull("key gives way to keys once merged", matcher.getKey());
    assertEquals(2, matcher.getKeys().size());
    assertTrue(matcher.getKeys().contains("alice@example.com"));
    assertTrue(matcher.getKeys().contains("bob@example.com"));
  }

  @Test
  public void addIfAbsentIgnoresAKeyAlreadyMergedIntoKeys() {
    LearnedRulesStore store = new LearnedRulesStore(tmp.getRoot().getAbsolutePath(), "account");
    store.addIfAbsent(rule("alice@example.com", "Spam"));
    store.addIfAbsent(rule("bob@example.com", "Spam")); // merged into the same rule

    boolean added = store.addIfAbsent(rule("alice@example.com", "Spam"));

    assertFalse(added);
    List<MailFilterRuleConfiguration> rules = store.load();
    assertEquals(1, rules.size());
    assertEquals(2, rules.get(0).getMatcher().getKeys().size());
  }

  @Test
  public void addIfAbsentCompactsPreExistingDuplicatesAlreadyInTheFile() {
    // Simulates a file written before merging existed: two separate rules, same matcher
    // type + same action, never merged with each other.
    LearnedRulesStore store = new LearnedRulesStore(tmp.getRoot().getAbsolutePath(), "account");
    store.save(Arrays.asList(rule("alice@example.com", "Spam"), rule("bob@example.com", "Spam")));

    boolean added = store.addIfAbsent(rule("carol@example.com", "Spam"));

    assertTrue(added);
    List<MailFilterRuleConfiguration> rules = store.load();
    assertEquals("the two pre-existing duplicates + the new key: a single rule", 1, rules.size());
    Set<String> keys = rules.get(0).getMatcher().getKeys();
    assertEquals(3, keys.size());
    assertTrue(keys.containsAll(Arrays.asList("alice@example.com", "bob@example.com", "carol@example.com")));
  }

  @Test
  public void addIfAbsentCompactsPreExistingDuplicatesEvenWhenTheKeyIsAlreadyKnown() {
    LearnedRulesStore store = new LearnedRulesStore(tmp.getRoot().getAbsolutePath(), "account");
    store.save(Arrays.asList(rule("alice@example.com", "Spam"), rule("bob@example.com", "Spam")));

    boolean added = store.addIfAbsent(rule("alice@example.com", "Spam"));

    assertFalse("alice was already known, nothing new learned", added);
    List<MailFilterRuleConfiguration> rules = store.load();
    assertEquals("the two pre-existing duplicates must still be compacted", 1, rules.size());
    assertEquals(2, rules.get(0).getMatcher().getKeys().size());
  }

  @Test
  public void twoAccountsDoNotShareTheSameFile() {
    LearnedRulesStore storeA = new LearnedRulesStore(tmp.getRoot().getAbsolutePath(), "account-a");
    LearnedRulesStore storeB = new LearnedRulesStore(tmp.getRoot().getAbsolutePath(), "account-b");

    storeA.addIfAbsent(rule("alice@example.com", "Spam"));

    assertEquals(1, storeA.load().size());
    assertTrue(storeB.load().isEmpty());
  }
}
