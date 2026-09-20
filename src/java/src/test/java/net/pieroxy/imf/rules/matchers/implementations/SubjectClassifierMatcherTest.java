package net.pieroxy.imf.rules.matchers.implementations;

import net.pieroxy.imf.detection.classifier.ClassifierCorpusStore;
import net.pieroxy.imf.detection.classifier.ClassifierExample;
import net.pieroxy.imf.detection.classifier.ClassifierLabel;
import net.pieroxy.imf.detection.classifier.SubjectClassifierTrainer;
import net.pieroxy.imf.config.MailFilterRuleMatcherConfiguration;
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

public class SubjectClassifierMatcherTest {
  private final Session session = Session.getDefaultInstance(new Properties());

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  /** modelFile null = no account context available, same as an unrelated matcher would see. */
  private static SubjectClassifierMatcher matcherFor(String threshold, File modelFile) {
    MailFilterRuleMatcherConfiguration config = new MailFilterRuleMatcherConfiguration();
    config.setType(MatcherType.SUBJECT_CLASSIFIER_EQUALS);
    config.setKey(threshold);
    return (SubjectClassifierMatcher) Matcher.build(config, new RuleContext(modelFile, null, null, null));
  }

  private MimeMessage messageWithSubject(String subject) throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setSubject(subject);
    return message;
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
  public void doesNotMatchWhenMessageHasNoSubject() throws Exception {
    SubjectClassifierMatcher matcher = matcherFor(">0.5", null);
    // No model file at all: if the subject check didn't short-circuit first, this would blow up
    // instead of returning notMatched().
    MimeMessage message = new MimeMessage(session);

    assertFalse(matcher.matches(message).matched());
  }

  @Test
  public void announcesInactiveStateAssoonAsConfiguredNotAtFirstMessage() throws Exception {
    File modelFile = tmp.newFile("does-not-exist.bin");
    tmp.getRoot().listFiles((dir, name) -> name.equals("does-not-exist.bin"))[0].delete(); // the file must not exist

    // setConfig() logs on ITS OWN logger (derived from the config), not accessible yet before
    // construction: so we capture via the root logger, which receives everything by propagation
    // (the default behavior as long as nothing calls setUseParentHandlers(false)).
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
    SubjectClassifierMatcher matcher = matcherFor(">0.5", modelFile); // already logs "inactive" once here, not captured

    List<LogRecord> records = new ArrayList<>();
    Handler capture = new Handler() {
      @Override public void publish(LogRecord record) { records.add(record); }
      @Override public void flush() {}
      @Override public void close() {}
    };
    matcher.getLogger().addHandler(capture);
    matcher.getLogger().setUseParentHandlers(false);
    try {
      assertFalse(matcher.matches(messageWithSubject("hello")).matched());
      assertFalse(matcher.matches(messageWithSubject("hello again")).matched());
      assertFalse(matcher.matches(messageWithSubject("and again")).matched());
    } finally {
      matcher.getLogger().removeHandler(capture);
      matcher.getLogger().setUseParentHandlers(true);
    }

    long inactiveLogs = records.stream().filter(r -> r.getMessage().contains("inactive")).count();
    assertEquals("already announced during setConfig(): no inspected message should re-log it", 0, inactiveLogs);
  }

  @Test
  public void classifiesObviouslySpammyAndHammySubjectsOnceTrained() throws Exception {
    ClassifierCorpusStore store = new ClassifierCorpusStore(tmp.getRoot().getAbsolutePath(), "account", 30);
    List<ClassifierExample> examples = new ArrayList<>();
    String[] spamSubjects = {
        "Buy cheap viagra now", "You have WON the lottery, claim your prize",
        "URGENT wire transfer needed today", "Free money click here now",
        "Congratulations you won a prize", "Claim your inheritance now urgent",
        "Hot singles in your area tonight", "Make money fast from home guaranteed",
        "Your account will be suspended click now", "Get rich quick guaranteed returns",
    };
    String[] hamSubjects = {
        "Meeting notes from yesterday", "Your invoice for March is ready",
        "Project status update for the team", "Lunch tomorrow at noon",
        "Weekly team sync agenda attached", "Quarterly report draft for review",
        "Reminder: dentist appointment Friday", "Photos from the weekend trip",
        "Updated schedule for next sprint", "Thanks for the feedback yesterday",
    };
    for (int i = 0; i < 6; i++) { // 60 examples per class: comfortably above the minimum (50)
      for (String s : spamSubjects) examples.add(example(s, ClassifierLabel.SPAM));
      for (String s : hamSubjects) examples.add(example(s, ClassifierLabel.HAM));
    }
    store.append(LocalDate.now(), examples);

    new SubjectClassifierTrainer(store).train();

    SubjectClassifierMatcher confidentSpam = matcherFor(">0.5", store.getModelFile());
    assertTrue("a clearly spammy subject must cross the threshold",
        confidentSpam.matches(messageWithSubject("Buy cheap viagra now, free money")).matched());
    assertFalse("a clearly legitimate subject must not cross the threshold",
        confidentSpam.matches(messageWithSubject("Meeting notes from yesterday's sync")).matched());

    SubjectClassifierMatcher confidentHam = matcherFor("<0.5", store.getModelFile());
    assertFalse("the < operator applies too: a spammy subject must not fall below the threshold",
        confidentHam.matches(messageWithSubject("Buy cheap viagra now, free money")).matched());
    assertTrue("a legitimate subject must fall below the threshold with <",
        confidentHam.matches(messageWithSubject("Meeting notes from yesterday's sync")).matched());
  }

  private static ClassifierExample example(String subject, ClassifierLabel label) {
    ClassifierExample e = new ClassifierExample();
    e.setSubject(subject);
    e.setLabel(label);
    e.setFrom(Collections.emptyList());
    e.setTo(Collections.emptyList());
    return e;
  }
}
