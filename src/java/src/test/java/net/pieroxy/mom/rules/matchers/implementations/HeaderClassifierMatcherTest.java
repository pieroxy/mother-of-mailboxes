package net.pieroxy.mom.rules.matchers.implementations;

import net.pieroxy.mom.detection.classifier.ClassifierCorpusStore;
import net.pieroxy.mom.detection.classifier.ClassifierExample;
import net.pieroxy.mom.detection.classifier.ClassifierLabel;
import net.pieroxy.mom.detection.classifier.HeaderClassifierTrainer;
import net.pieroxy.mom.config.general.MailFilterRuleMatcherConfiguration;
import net.pieroxy.mom.rules.RuleContext;
import net.pieroxy.mom.rules.matchers.Matcher;
import net.pieroxy.mom.rules.matchers.MatcherType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.mail.Session;
import javax.mail.internet.InternetAddress;
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

public class HeaderClassifierMatcherTest {
  private final Session session = Session.getDefaultInstance(new Properties());

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  /** modelFile null = no account context available, same as an unrelated matcher would see. */
  private static HeaderClassifierMatcher matcherFor(String threshold, File modelFile) {
    MailFilterRuleMatcherConfiguration config = new MailFilterRuleMatcherConfiguration();
    config.setType(MatcherType.HEADER_CLASSIFIER_EQUALS);
    config.setKey(threshold);
    return (HeaderClassifierMatcher) Matcher.build(config, new RuleContext(null, modelFile, null, null));
  }

  private static ClassifierExample example(ClassifierLabel label) {
    ClassifierExample e = new ClassifierExample();
    e.setLabel(label);
    e.setFrom(Collections.emptyList());
    e.setTo(Collections.emptyList());
    return e;
  }

  private static ClassifierExample spamLikeExample() {
    ClassifierExample e = example(ClassifierLabel.SPAM);
    e.setFrom(List.of("deals@spammy.example.net"));
    e.setPrecedence("bulk");
    e.setListUnsubscribePresent(true);
    e.setReturnPathMismatch(true);
    return e;
  }

  private static ClassifierExample hamLikeExample() {
    ClassifierExample e = example(ClassifierLabel.HAM);
    e.setFrom(List.of("alice@example.com"));
    e.setReply(true);
    e.setReturnPathMismatch(false);
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
    HeaderClassifierMatcher matcher = matcherFor(">0.5", null);
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
    HeaderClassifierMatcher matcher = matcherFor(">0.5", modelFile); // already logs "inactive" once here, not captured

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
  public void classifiesObviouslySpammyAndHammyHeadersOnceTrained() throws Exception {
    ClassifierCorpusStore store = new ClassifierCorpusStore(tmp.getRoot().getAbsolutePath(), "account", 30);
    List<ClassifierExample> examples = new ArrayList<>();
    for (int i = 0; i < 60; i++) { // comfortably above the minimum (50)
      examples.add(spamLikeExample());
      examples.add(hamLikeExample());
    }
    store.append(LocalDate.now(), examples);

    new HeaderClassifierTrainer(store).train();

    MimeMessage spammy = new MimeMessage(session);
    spammy.setFrom(new InternetAddress("deals@spammy.example.net"));
    spammy.addHeader("Precedence", "bulk");
    spammy.addHeader("List-Unsubscribe", "<mailto:unsub@spammy.example.net>");
    spammy.addHeader("Return-Path", "<bounce@totally-different.example.org>");

    MimeMessage legit = new MimeMessage(session);
    legit.setFrom(new InternetAddress("alice@example.com"));
    legit.addHeader("In-Reply-To", "<original@example.com>");
    legit.addHeader("Return-Path", "<alice@example.com>");

    HeaderClassifierMatcher confidentSpam = matcherFor(">0.5", store.getHeaderModelFile());
    assertTrue("clearly spammy headers must cross the threshold", confidentSpam.matches(spammy).matched());
    assertFalse("clearly legitimate headers must not cross the threshold", confidentSpam.matches(legit).matched());

    HeaderClassifierMatcher confidentHam = matcherFor("<0.5", store.getHeaderModelFile());
    assertFalse("the < operator applies too: spammy headers must not fall below the threshold",
        confidentHam.matches(spammy).matched());
    assertTrue("legitimate headers must fall below the threshold with <", confidentHam.matches(legit).matched());
  }
}
