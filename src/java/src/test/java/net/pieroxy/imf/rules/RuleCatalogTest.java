package net.pieroxy.imf.rules;

import net.pieroxy.imf.config.general.MailFilterRuleActionConfiguration;
import net.pieroxy.imf.config.general.MailFilterRuleConfiguration;
import net.pieroxy.imf.config.general.MailFilterRuleMatcherConfiguration;
import net.pieroxy.imf.learning.LearnedRulesStore;
import net.pieroxy.imf.rules.actions.ActionType;
import net.pieroxy.imf.rules.matchers.MatcherType;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class RuleCatalogTest {

  @org.junit.Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private final Session session = Session.getDefaultInstance(new Properties());

  private static MailFilterRuleConfiguration rule(String fromKey) {
    MailFilterRuleMatcherConfiguration matcher = new MailFilterRuleMatcherConfiguration();
    matcher.setType(MatcherType.FROM_ADDRESS_EQUALS);
    matcher.setKey(fromKey);

    MailFilterRuleActionConfiguration action = new MailFilterRuleActionConfiguration();
    action.setType(ActionType.READ);

    MailFilterRuleConfiguration r = new MailFilterRuleConfiguration();
    r.setMatcher(matcher);
    r.setAction(action);
    return r;
  }

  @Test
  public void combinesManualAndLearnedRules() throws Exception {
    LearnedRulesStore store = new LearnedRulesStore(tmp.getRoot().getAbsolutePath(), "account");
    store.addIfAbsent(rule("learned@example.com"));
    RuleCatalog catalog = new RuleCatalog(Arrays.asList(rule("manual@example.com")), store);

    List<RuleInterface> rules = catalog.get();

    // Not present explicitly: the manual rule is the only entry in get(); learned rules run
    // implicitly via getLearnedRulesFallback() (see RuleHelper.processRules).
    assertEquals(1, rules.size());
    MimeMessage manualMatch = new MimeMessage(session);
    manualMatch.setFrom(new InternetAddress("manual@example.com"));
    MimeMessage learnedMatch = new MimeMessage(session);
    learnedMatch.setFrom(new InternetAddress("learned@example.com"));

    assertTrue(rules.stream().anyMatch(r -> r.apply(manualMatch).ruleApplied()));
    assertTrue(catalog.getLearnedRulesFallback().apply(learnedMatch).ruleApplied());
  }

  @Test
  public void cachesTheBuiltListUntilInvalidated() {
    LearnedRulesStore store = new LearnedRulesStore(tmp.getRoot().getAbsolutePath(), "account");
    RuleCatalog catalog = new RuleCatalog(null, store);

    List<RuleInterface> first = catalog.get();
    store.addIfAbsent(rule("new@example.com")); // change external to the cache

    assertSame("get() must not re-read until invalidated", first, catalog.get());
  }

  /**
   * With no LEARNED_RULES entry in config.json, learned rules never appear in get() itself —
   * they run implicitly through getLearnedRulesFallback() (see RuleHelper.processRules) — but
   * that fallback must still reflect newly-learned rules once the catalog is rebuilt.
   */
  @Test
  public void rebuildsAfterInvalidate() throws Exception {
    LearnedRulesStore store = new LearnedRulesStore(tmp.getRoot().getAbsolutePath(), "account");
    RuleCatalog catalog = new RuleCatalog(null, store);
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("new@example.com"));

    assertTrue(catalog.get().isEmpty());
    assertFalse(catalog.getLearnedRulesFallback().apply(message).ruleApplied());

    store.addIfAbsent(rule("new@example.com"));
    catalog.invalidate();

    assertTrue(catalog.getLearnedRulesFallback().apply(message).ruleApplied());
  }

  /** A LEARNED_RULES entry may carry neither a matcher nor an action. */
  @Test(expected = IllegalStateException.class)
  public void learnedRulesEntryRejectsAMatcherOrAction() {
    MailFilterRuleConfiguration entry = new MailFilterRuleConfiguration();
    entry.setType(RuleType.LEARNED_RULES);
    entry.setMatcher(rule("x@example.com").getMatcher());

    LearnedRulesStore store = new LearnedRulesStore(tmp.getRoot().getAbsolutePath(), "account");
    RuleCatalog catalog = new RuleCatalog(List.of(entry), store);

    catalog.get();
  }

  /** At most one LEARNED_RULES entry per account: a second one is rejected, not silently ignored. */
  @Test(expected = IllegalStateException.class)
  public void onlyOneLearnedRulesEntryIsAllowed() {
    MailFilterRuleConfiguration first = new MailFilterRuleConfiguration();
    first.setType(RuleType.LEARNED_RULES);
    MailFilterRuleConfiguration second = new MailFilterRuleConfiguration();
    second.setType(RuleType.LEARNED_RULES);

    LearnedRulesStore store = new LearnedRulesStore(tmp.getRoot().getAbsolutePath(), "account");
    RuleCatalog catalog = new RuleCatalog(Arrays.asList(first, second), store);

    catalog.get();
  }

  /**
   * A LEARNED_RULES entry placed in the middle of config.json runs right there, in list order —
   * the whole point of the feature (see the discussion this implements: learned rules no longer
   * have to run only after every manual rule).
   */
  @Test
  public void learnedRulesEntryRunsAtItsPositionInTheList() throws Exception {
    LearnedRulesStore store = new LearnedRulesStore(tmp.getRoot().getAbsolutePath(), "account");
    store.addIfAbsent(rule("learned@example.com"));

    MailFilterRuleConfiguration learnedRulesEntry = new MailFilterRuleConfiguration();
    learnedRulesEntry.setType(RuleType.LEARNED_RULES);

    RuleCatalog catalog = new RuleCatalog(Arrays.asList(rule("before@example.com"), learnedRulesEntry, rule("after@example.com")), store);

    List<RuleInterface> rules = catalog.get();
    assertEquals(3, rules.size());
    assertTrue(rules.get(1) instanceof LearnedRulesGroupRule);
    assertSame("the same instance must be used both inline and as the implicit fallback",
        catalog.getLearnedRulesFallback(), rules.get(1));
  }
}
