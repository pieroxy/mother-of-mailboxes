package net.pieroxy.imf.rules;

import net.pieroxy.imf.config.general.MailFilterRuleActionConfiguration;
import net.pieroxy.imf.config.general.MailFilterRuleConfiguration;
import net.pieroxy.imf.config.general.MailFilterRuleMatcherConfiguration;
import net.pieroxy.imf.rules.actions.ActionType;
import net.pieroxy.imf.rules.matchers.MatcherType;
import org.junit.Test;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests the top-level fallback semantics of RuleHelper.processRules: learned rules must always
 * get a chance to run exactly once — either because a LEARNED_RULES entry is reached during the
 * main loop, or, if none is present (or it's never reached), as an implicit fallback once the
 * main loop reaches its natural end without a blocking match.
 */
public class RuleHelperTest {
  private final Session session = Session.getDefaultInstance(new Properties());
  private final Logger logger = Logger.getLogger("test");

  private Message messageFrom(String address) throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress(address));
    return message;
  }

  private static Rule blockingRule(String fromKey) {
    MailFilterRuleMatcherConfiguration matcher = new MailFilterRuleMatcherConfiguration();
    matcher.setType(MatcherType.FROM_ADDRESS_EQUALS);
    matcher.setKey(fromKey);
    MailFilterRuleActionConfiguration action = new MailFilterRuleActionConfiguration();
    action.setType(ActionType.READ);
    MailFilterRuleConfiguration config = new MailFilterRuleConfiguration();
    config.setMatcher(matcher);
    config.setAction(action);
    return new Rule(config);
  }

  /** A stand-in for LearnedRulesGroupRule: just records whether it was asked to run. */
  private static class SpyRule implements RuleInterface {
    boolean invoked = false;
    private final boolean matches;
    SpyRule(boolean matches) { this.matches = matches; }
    @Override public String describe() { return "Spy"; }
    @Override public RuleExecutionResult apply(Message message) {
      invoked = true;
      return matches ? new RuleExecutionResult(true, false, true) : new RuleExecutionResult(false, true, true);
    }
  }

  @Test
  public void fallbackRunsWhenNoRuleInTheListBlocks() throws Exception {
    SpyRule fallback = new SpyRule(true);

    boolean matched = RuleHelper.processRules(List.of(blockingRule("nobody@example.com")), fallback,
        messageFrom("alice@example.com"), logger, "test");

    assertTrue("the non-matching manual rule must not prevent the fallback", fallback.invoked);
    assertTrue(matched);
  }

  @Test
  public void fallbackIsSkippedWhenAnEarlierRuleAlreadyBlocked() throws Exception {
    SpyRule fallback = new SpyRule(true);

    boolean matched = RuleHelper.processRules(List.of(blockingRule("alice@example.com")), fallback,
        messageFrom("alice@example.com"), logger, "test");

    assertFalse("a blocking manual match must stop evaluation before the fallback ever runs", fallback.invoked);
    assertTrue(matched);
  }

  @Test
  public void fallbackIsSkippedWhenALearnedRulesEntryAlreadyRanInline() throws Exception {
    SpyRule inlineLearnedRules = new SpyRule(false);
    SpyRule fallback = new SpyRule(true);

    RuleHelper.processRules(List.of(inlineLearnedRules), fallback, messageFrom("alice@example.com"), logger, "test");

    assertTrue(inlineLearnedRules.invoked);
    assertFalse("learnedRulesExecuted from the inline entry must prevent a second, redundant run", fallback.invoked);
  }
}
