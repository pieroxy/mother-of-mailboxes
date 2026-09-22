package net.pieroxy.mom.rules;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.pieroxy.mom.config.general.MailFilterRuleActionConfiguration;
import net.pieroxy.mom.config.general.MailFilterRuleConfiguration;
import net.pieroxy.mom.config.general.MailFilterRuleMatcherConfiguration;
import net.pieroxy.mom.rules.actions.ActionType;
import net.pieroxy.mom.rules.matchers.MatcherType;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests the top-level fallback semantics of RuleHelper.processRules: learned rules must always
 * get a chance to run exactly once — either because a LEARNED_RULES entry is reached during the
 * main loop, or, if none is present (or it's never reached), as an implicit fallback once the
 * main loop reaches its natural end without a blocking match.
 */
public class RuleHelperTest {
  @org.junit.Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private static final Gson GSON = new Gson();

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
      return matches ? new RuleExecutionResult(true, false, true, "Spy") : new RuleExecutionResult(false, true, true, null);
    }
  }

  @Test
  public void fallbackRunsWhenNoRuleInTheListBlocks() throws Exception {
    SpyRule fallback = new SpyRule(true);

    boolean matched = RuleHelper.processRules(List.of(blockingRule("nobody@example.com")), fallback,
        messageFrom("alice@example.com"), logger, "test", RuleContext.EMPTY);

    assertTrue("the non-matching manual rule must not prevent the fallback", fallback.invoked);
    assertTrue(matched);
  }

  @Test
  public void fallbackIsSkippedWhenAnEarlierRuleAlreadyBlocked() throws Exception {
    SpyRule fallback = new SpyRule(true);

    boolean matched = RuleHelper.processRules(List.of(blockingRule("alice@example.com")), fallback,
        messageFrom("alice@example.com"), logger, "test", RuleContext.EMPTY);

    assertFalse("a blocking manual match must stop evaluation before the fallback ever runs", fallback.invoked);
    assertTrue(matched);
  }

  @Test
  public void fallbackIsSkippedWhenALearnedRulesEntryAlreadyRanInline() throws Exception {
    SpyRule inlineLearnedRules = new SpyRule(false);
    SpyRule fallback = new SpyRule(true);

    RuleHelper.processRules(List.of(inlineLearnedRules), fallback, messageFrom("alice@example.com"), logger, "test", RuleContext.EMPTY);

    assertTrue(inlineLearnedRules.invoked);
    assertFalse("learnedRulesExecuted from the inline entry must prevent a second, redundant run", fallback.invoked);
  }

  private JsonObject onlyProcessedEvent(File statsDir) throws Exception {
    String today = LocalDate.now(ZoneOffset.UTC).toString();
    List<String> lines = Files.readAllLines(new File(statsDir, "stats-" + today + ".json").toPath());
    assertEquals(1, lines.size());
    return GSON.fromJson(lines.get(0), JsonObject.class);
  }

  @Test
  public void recordsAProcessedMatchEventWhenAManualRuleBlocks() throws Exception {
    File statsDir = new File(tmp.getRoot(), "logs");
    SpyRule fallback = new SpyRule(true);

    RuleHelper.processRules(List.of(blockingRule("alice@example.com")), fallback,
        messageFrom("alice@example.com"), logger, "test", new RuleContext(null, null, null, statsDir));

    JsonObject event = onlyProcessedEvent(statsDir);
    assertEquals("PROCESSED", event.get("type").getAsString());
    assertEquals("MATCH", event.get("result").getAsString());
    assertEquals("FromAddressMatcher(alice@example.com)", event.get("matcher").getAsString());
    assertTrue(event.get("processedMs").getAsLong() >= 0);
  }

  @Test
  public void recordsAProcessedMatchEventWhenTheLearnedRulesFallbackBlocks() throws Exception {
    File statsDir = new File(tmp.getRoot(), "logs");
    SpyRule fallback = new SpyRule(true); // keepProcessing=false: blocks

    RuleHelper.processRules(List.of(blockingRule("nobody@example.com")), fallback,
        messageFrom("alice@example.com"), logger, "test", new RuleContext(null, null, null, statsDir));

    JsonObject event = onlyProcessedEvent(statsDir);
    assertEquals("MATCH", event.get("result").getAsString());
    assertEquals("the fallback's own matched description, not the never-matching manual rule's",
        "Spy", event.get("matcher").getAsString());
  }

  @Test
  public void recordsAProcessedPassEventWhenNothingBlocks() throws Exception {
    File statsDir = new File(tmp.getRoot(), "logs");
    SpyRule fallback = new SpyRule(false); // never matches: keepProcessing=true

    RuleHelper.processRules(List.of(blockingRule("nobody@example.com")), fallback,
        messageFrom("alice@example.com"), logger, "test", new RuleContext(null, null, null, statsDir));

    JsonObject event = onlyProcessedEvent(statsDir);
    assertEquals("PASS", event.get("result").getAsString());
    assertFalse("a PASS event has no matcher field", event.has("matcher"));
  }
}
