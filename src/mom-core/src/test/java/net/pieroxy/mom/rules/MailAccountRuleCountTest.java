package net.pieroxy.mom.rules;

import net.pieroxy.mom.config.general.MailFilterRuleActionConfiguration;
import net.pieroxy.mom.config.general.MailFilterRuleConfiguration;
import net.pieroxy.mom.config.general.MailFilterRuleMatcherConfiguration;
import net.pieroxy.mom.rules.actions.ActionType;
import net.pieroxy.mom.rules.matchers.MatcherType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Checks {@link MailAccount#getRuleCount()}/{@link MailAccount#getActiveRuleCount()} — see {@code AccountsApi}. */
public class MailAccountRuleCountTest extends AbstractMailAccountTest {

  private static MailFilterRuleConfiguration ruleWithAction(ActionType actionType) {
    MailFilterRuleMatcherConfiguration matcher = new MailFilterRuleMatcherConfiguration();
    matcher.setType(MatcherType.FROM_DOMAIN_EQUALS);
    matcher.setKey("example.com");
    MailFilterRuleActionConfiguration action = new MailFilterRuleActionConfiguration();
    action.setType(actionType);
    if (actionType != ActionType.NOOP) action.setKey("Spam");
    MailFilterRuleConfiguration rule = new MailFilterRuleConfiguration();
    rule.setMatcher(matcher);
    rule.setAction(action);
    return rule;
  }

  private static MailFilterRuleConfiguration learnedRulesEntry() {
    MailFilterRuleConfiguration rule = new MailFilterRuleConfiguration();
    rule.setType(RuleType.LEARNED_RULES);
    return rule;
  }

  @Test
  public void countsZeroRulesWhenNoneConfigured() {
    MailAccount account = accountWith();

    assertEquals(0, account.getRuleCount());
    assertEquals(0, account.getActiveRuleCount());
  }

  @Test
  public void countsAllRulesButOnlyNonNoopOnesAsActive() {
    MailAccount account = accountWith(ruleWithAction(ActionType.MOVE_TO), ruleWithAction(ActionType.NOOP), ruleWithAction(ActionType.READ));

    assertEquals(3, account.getRuleCount());
    assertEquals(2, account.getActiveRuleCount());
  }

  @Test
  public void aLearnedRulesEntryCountsAsARuleButNotAsActive() {
    MailAccount account = accountWith(ruleWithAction(ActionType.MOVE_TO), learnedRulesEntry());

    assertEquals(2, account.getRuleCount());
    assertEquals(1, account.getActiveRuleCount());
  }
}
