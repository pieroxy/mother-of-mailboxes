package net.pieroxy.imf.rules;

import net.pieroxy.imf.config.general.MailFilterRuleActionConfiguration;
import net.pieroxy.imf.config.general.MailFilterRuleConfiguration;
import net.pieroxy.imf.config.general.MailFilterRuleMatcherConfiguration;
import net.pieroxy.imf.rules.actions.ActionType;
import net.pieroxy.imf.rules.matchers.MatcherType;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests Rule end to end: builds the matcher/action tree via the factories (Matcher.build /
 * Action.build) from a config, then evaluates it against a message.
 */
public class RuleTest {
  @org.junit.Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private final Session session = Session.getDefaultInstance(new Properties());

  private MimeMessage messageFrom(String address) throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress(address));
    return message;
  }

  private static MailFilterRuleMatcherConfiguration fromEquals(String email) {
    MailFilterRuleMatcherConfiguration c = new MailFilterRuleMatcherConfiguration();
    c.setType(MatcherType.FROM_EQUALS);
    c.setKey(email);
    return c;
  }

  /**
   * A childless AND action: succeeds vacuously without touching the message. Used here to test
   * matcher-driven triggering without depending on MoveToAction's real IMAP mechanics (which
   * needs a real Folder/Store, absent from a test MimeMessage).
   */
  private static MailFilterRuleActionConfiguration noopAction() {
    MailFilterRuleActionConfiguration c = new MailFilterRuleActionConfiguration();
    c.setType(ActionType.AND);
    return c;
  }

  @Test
  public void appliesWhenMatcherMatches() throws Exception {
    MailFilterRuleConfiguration config = new MailFilterRuleConfiguration();
    config.setMatcher(fromEquals("alice@example.com"));
    config.setAction(noopAction());

    Rule rule = new Rule(config);

    assertTrue(rule.apply(messageFrom("alice@example.com")).ruleApplied());
  }

  @Test
  public void recordsAStatsEventWhenTheMatcherMatches() throws Exception {
    File statsDir = new File(tmp.getRoot(), "logs");
    MailFilterRuleConfiguration config = new MailFilterRuleConfiguration();
    config.setMatcher(fromEquals("alice@example.com"));
    config.setAction(noopAction());
    RuleContext context = new RuleContext(null, null, null, statsDir);

    Rule rule = new Rule(config, context);
    rule.apply(messageFrom("alice@example.com"));

    String today = LocalDate.now(ZoneOffset.UTC).toString();
    List<String> lines = Files.readAllLines(new File(statsDir, "stats-" + today + ".json").toPath());
    assertEquals(1, lines.size());
    assertTrue(lines.get(0).contains("FromExactMatcher(alice@example.com)"));
  }

  @Test
  public void doesNotRecordAStatsEventWhenTheMatcherDoesNotMatch() throws Exception {
    File statsDir = new File(tmp.getRoot(), "logs");
    MailFilterRuleConfiguration config = new MailFilterRuleConfiguration();
    config.setMatcher(fromEquals("alice@example.com"));
    config.setAction(noopAction());
    RuleContext context = new RuleContext(null, null, null, statsDir);

    Rule rule = new Rule(config, context);
    rule.apply(messageFrom("carol@example.com"));

    assertFalse(statsDir.isDirectory());
  }

  @Test
  public void doesNotApplyWhenMatcherDoesNotMatch() throws Exception {
    MailFilterRuleConfiguration config = new MailFilterRuleConfiguration();
    config.setMatcher(fromEquals("alice@example.com"));
    config.setAction(noopAction());

    Rule rule = new Rule(config);

    assertFalse(rule.apply(messageFrom("carol@example.com")).ruleApplied());
  }

  @Test
  public void appliesWithCompositeAndMatcher() throws Exception {
    MailFilterRuleMatcherConfiguration and = new MailFilterRuleMatcherConfiguration();
    and.setType(MatcherType.AND);
    and.setChildren(Arrays.asList(fromEquals("alice@example.com"), fromEquals("alice@example.com")));

    MailFilterRuleConfiguration config = new MailFilterRuleConfiguration();
    config.setMatcher(and);
    config.setAction(noopAction());

    Rule rule = new Rule(config);

    assertTrue(rule.apply(messageFrom("alice@example.com")).ruleApplied());
    assertFalse(rule.apply(messageFrom("bob@example.com")).ruleApplied());
  }

  /**
   * Rule.apply() logs the MatchResult's "debugString" (e.g. "FromExactMatcher(...)"), not just
   * the matcher's class name — useful for knowing, on a matcher with several "keys", which one
   * precisely made the rule match.
   */
  @Test
  public void logsTheMatcherDebugStringWhenARuleMatches() throws Exception {
    MailFilterRuleConfiguration config = new MailFilterRuleConfiguration();
    config.setMatcher(fromEquals("alice@example.com"));
    config.setAction(noopAction());
    Rule rule = new Rule(config);

    List<LogRecord> records = new ArrayList<>();
    Handler capture = new Handler() {
      @Override public void publish(LogRecord record) { records.add(record); }
      @Override public void flush() {}
      @Override public void close() {}
    };
    Logger matcherLogger = Logger.getLogger(net.pieroxy.imf.rules.matchers.Matcher.class.getName());
    matcherLogger.addHandler(capture);
    try {
      rule.apply(messageFrom("alice@example.com"));
    } finally {
      matcherLogger.removeHandler(capture);
    }

    assertTrue(records.stream().anyMatch(r ->
            r.getMessage().contains("FromExactMatcher(alice@example.com) matched message from")));
  }

  @Test
  public void evaluateReturnsFalseForEmptyList() throws Exception {
    assertFalse(RuleHelper.evaluate(List.of(), messageFrom("alice@example.com"), Logger.getLogger("test"), "test").ruleApplied());
  }

  @Test
  public void evaluateReturnsFalseWhenNoRuleMatches() throws Exception {
    MailFilterRuleConfiguration config = new MailFilterRuleConfiguration();
    config.setMatcher(fromEquals("alice@example.com"));
    config.setAction(noopAction());
    List<RuleInterface> rules = List.of(new Rule(config));

    assertFalse(RuleHelper.evaluate(rules, messageFrom("bob@example.com"), Logger.getLogger("test"), "test").ruleApplied());
  }

  @Test
  public void evaluateSkipsNonMatchingRulesAndAppliesTheOneThatMatches() throws Exception {
    MailFilterRuleConfiguration first = new MailFilterRuleConfiguration();
    first.setMatcher(fromEquals("carol@example.com"));
    first.setAction(noopAction());

    MailFilterRuleConfiguration second = new MailFilterRuleConfiguration();
    second.setMatcher(fromEquals("alice@example.com"));
    second.setAction(noopAction());

    List<RuleInterface> rules = Arrays.asList(new Rule(first), new Rule(second));

    assertTrue(RuleHelper.evaluate(rules, messageFrom("alice@example.com"), Logger.getLogger("test"), "test").ruleApplied());
  }

  @Test
  public void describeMentionsKeepProcessingWhenSet() throws Exception {
    MailFilterRuleConfiguration config = new MailFilterRuleConfiguration();
    config.setMatcher(fromEquals("alice@example.com"));
    config.setAction(noopAction());
    config.setKeepProcessing(true);

    assertTrue(new Rule(config).describe().endsWith(" [keepProcessing]"));
  }

  @Test
  public void describeOmitsKeepProcessingByDefault() throws Exception {
    MailFilterRuleConfiguration config = new MailFilterRuleConfiguration();
    config.setMatcher(fromEquals("alice@example.com"));
    config.setAction(noopAction());

    assertFalse(new Rule(config).describe().contains("keepProcessing"));
  }

  @Test
  public void aKeepProcessingRuleAloneStillCountsAsAMatch() throws Exception {
    MailFilterRuleConfiguration config = new MailFilterRuleConfiguration();
    config.setMatcher(fromEquals("alice@example.com"));
    config.setAction(noopAction());
    config.setKeepProcessing(true);
    List<RuleInterface> rules = List.of(new Rule(config));

    assertTrue(RuleHelper.evaluate(rules, messageFrom("alice@example.com"), Logger.getLogger("test"), "test").ruleApplied());
  }

  /**
   * Three rules that all match: the first (keepProcessing) lets evaluation carry on to the
   * second (default behavior: it stops evaluation there), so the third is never evaluated —
   * checked by counting the matcher's match logs, not just the overall result, to prove the
   * third is genuinely never reached.
   */
  @Test
  public void keepProcessingLetsEvaluationContinueUntilANonKeepProcessingRuleMatches() throws Exception {
    MailFilterRuleConfiguration first = new MailFilterRuleConfiguration();
    first.setMatcher(fromEquals("alice@example.com"));
    first.setAction(noopAction());
    first.setKeepProcessing(true);

    MailFilterRuleConfiguration second = new MailFilterRuleConfiguration();
    second.setMatcher(fromEquals("alice@example.com"));
    second.setAction(noopAction());
    // keepProcessing defaults to false: evaluation must stop here.

    MailFilterRuleConfiguration third = new MailFilterRuleConfiguration();
    third.setMatcher(fromEquals("alice@example.com"));
    third.setAction(noopAction());

    List<RuleInterface> rules = Arrays.asList(new Rule(first), new Rule(second), new Rule(third));

    List<LogRecord> records = new ArrayList<>();
    Handler capture = new Handler() {
      @Override public void publish(LogRecord record) { records.add(record); }
      @Override public void flush() {}
      @Override public void close() {}
    };
    Logger matcherLogger = Logger.getLogger(net.pieroxy.imf.rules.matchers.Matcher.class.getName());
    matcherLogger.addHandler(capture);
    boolean matched;
    try {
      matched = RuleHelper.evaluate(rules, messageFrom("alice@example.com"), Logger.getLogger("test"), "test").ruleApplied();
    } finally {
      matcherLogger.removeHandler(capture);
    }

    assertTrue(matched);
    long matchLogCount = records.stream()
            .filter(r -> r.getMessage() != null && r.getMessage().contains("matched message from"))
            .count();
    assertEquals("first (keepProcessing) and second must match, third must never be reached", 2, matchLogCount);
  }
}
