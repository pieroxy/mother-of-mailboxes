package net.pieroxy.imf.rules.matchers.implementations;

import net.pieroxy.imf.detection.classifier.BodyClassifierTrainer;
import net.pieroxy.imf.detection.classifier.ClassifierCorpusStore;
import net.pieroxy.imf.detection.classifier.ClassifierExample;
import net.pieroxy.imf.detection.classifier.ClassifierLabel;
import net.pieroxy.imf.config.general.MailFilterRuleMatcherConfiguration;
import net.pieroxy.imf.rules.RuleContext;
import net.pieroxy.imf.rules.matchers.Matcher;
import net.pieroxy.imf.rules.matchers.MatcherType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class BodyClassifierMatcherTest {
  private final Session session = Session.getDefaultInstance(new Properties());

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  /** modelFile null = no account context available, same as an unrelated matcher would see. */
  private static BodyClassifierMatcher matcherFor(String threshold, File modelFile) {
    MailFilterRuleMatcherConfiguration config = new MailFilterRuleMatcherConfiguration();
    config.setType(MatcherType.BODY_CLASSIFIER_EQUALS);
    config.setKey(threshold);
    return (BodyClassifierMatcher) Matcher.build(config, new RuleContext(null, null, modelFile, null));
  }

  private MimeMessage messageWithHtmlBody(String html) throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setContent(html, "text/html");
    message.saveChanges();
    return message;
  }

  private static ClassifierExample example(String bodyText, ClassifierLabel label) {
    ClassifierExample e = new ClassifierExample();
    e.setBodyText(bodyText);
    e.setLabel(label);
    e.setFrom(Collections.emptyList());
    e.setTo(Collections.emptyList());
    return e;
  }

  @Test
  public void rejectsAMalformedThreshold() {
    try {
      matcherFor("not-a-threshold", null);
      fail("should have thrown");
    } catch (IllegalArgumentException expected) {
      // ok
    }
  }

  @Test
  public void rejectsAMissingThreshold() {
    try {
      matcherFor(null, null);
      fail("should have thrown");
    } catch (IllegalArgumentException expected) {
      // ok
    }
  }

  @Test
  public void doesNotMatchWhenNoModelContextIsSet() throws Exception {
    BodyClassifierMatcher matcher = matcherFor(">0.5", null);
    MimeMessage message = new MimeMessage(session);

    assertFalse(matcher.matches(message).matched());
  }

  @Test
  public void announcesInactiveStateAssoonAsConfiguredNotAtFirstMessage() throws Exception {
    File modelFile = tmp.newFile("does-not-exist.bin");
    tmp.getRoot().listFiles((dir, name) -> name.equals("does-not-exist.bin"))[0].delete();

    List<LogRecord> records = new ArrayList<>();
    Handler capture = new Handler() {
      @Override public void publish(LogRecord record) { records.add(record); }
      @Override public void flush() {}
      @Override public void close() {}
    };
    Logger root = Logger.getLogger("");
    root.addHandler(capture);
    try {
      matcherFor(">0.5", modelFile); // the check + log must happen here, not on the first matches() call
    } finally {
      root.removeHandler(capture);
    }

    assertTrue("the state must be announced at construction time, not on the first message received",
        records.stream().anyMatch(r -> r.getMessage() != null && r.getMessage().contains("inactive")));
  }

  @Test
  public void doesNotReannounceInactiveStateOnEveryMessageAfterTheInitialCheck() throws Exception {
    File modelFile = tmp.newFile("does-not-exist.bin");
    tmp.getRoot().listFiles((dir, name) -> name.equals("does-not-exist.bin"))[0].delete();
    BodyClassifierMatcher matcher = matcherFor(">0.5", modelFile); // already logs "inactive" once here, not captured

    List<LogRecord> records = new ArrayList<>();
    Handler capture = new Handler() {
      @Override public void publish(LogRecord record) { records.add(record); }
      @Override public void flush() {}
      @Override public void close() {}
    };
    matcher.getLogger().addHandler(capture);
    matcher.getLogger().setUseParentHandlers(false);
    try {
      assertFalse(matcher.matches(new MimeMessage(session)).matched());
      assertFalse(matcher.matches(new MimeMessage(session)).matched());
    } finally {
      matcher.getLogger().removeHandler(capture);
      matcher.getLogger().setUseParentHandlers(true);
    }

    long inactiveLogs = records.stream().filter(r -> r.getMessage().contains("inactive")).count();
    assertEquals("already announced during setConfig(): no inspected message should re-log it", 0, inactiveLogs);
  }

  @Test
  public void classifiesObviouslySpammyAndHammyBodiesOnceTrained() throws Exception {
    ClassifierCorpusStore store = new ClassifierCorpusStore(tmp.getRoot().getAbsolutePath(), "account", 30);
    List<ClassifierExample> examples = new ArrayList<>();
    String[] spamBodies = {
        "Buy cheap viagra now click here", "You have WON the lottery claim your prize now",
        "URGENT wire transfer needed today act now", "Free money click here now guaranteed",
        "Congratulations you won a prize claim now", "Claim your inheritance now urgent reply",
        "Hot singles in your area tonight click", "Make money fast from home guaranteed now",
        "Your account will be suspended click now", "Get rich quick guaranteed returns now",
    };
    String[] hamBodies = {
        "Meeting notes from yesterday attached below", "Your invoice for March is ready attached",
        "Project status update for the team below", "Lunch tomorrow at noon in the kitchen",
        "Weekly team sync agenda attached below", "Quarterly report draft for review attached",
        "Reminder dentist appointment Friday morning", "Photos from the weekend trip attached",
        "Updated schedule for next sprint below", "Thanks for the feedback yesterday appreciated",
    };
    for (int i = 0; i < 16; i++) { // 160 examples per class: comfortably above the minimum (150)
      for (String s : spamBodies) examples.add(example(s, ClassifierLabel.SPAM));
      for (String s : hamBodies) examples.add(example(s, ClassifierLabel.HAM));
    }
    store.append(LocalDate.now(), examples);

    new BodyClassifierTrainer(store).train();

    BodyClassifierMatcher confidentSpam = matcherFor(">0.5", store.getBodyModelFile());
    assertTrue("a clearly spammy body must cross the threshold",
        confidentSpam.matches(messageWithHtmlBody("<p>Buy cheap viagra now, free money guaranteed</p>")).matched());
    assertFalse("a clearly legitimate body must not cross the threshold",
        confidentSpam.matches(messageWithHtmlBody("<p>Meeting notes from yesterday's sync attached</p>")).matched());

    BodyClassifierMatcher confidentHam = matcherFor("<0.5", store.getBodyModelFile());
    assertFalse("the < operator applies too: a spammy body must not fall below the threshold",
        confidentHam.matches(messageWithHtmlBody("<p>Buy cheap viagra now, free money guaranteed</p>")).matched());
    assertTrue("a legitimate body must fall below the threshold with <",
        confidentHam.matches(messageWithHtmlBody("<p>Meeting notes from yesterday's sync attached</p>")).matched());
  }
}
