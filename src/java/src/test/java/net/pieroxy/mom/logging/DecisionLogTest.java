package net.pieroxy.mom.logging;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.jpountz.lz4.LZ4FrameInputStream;
import net.pieroxy.mom.utils.logging.DecisionLog;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DecisionLogTest {
  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private static final Gson GSON = new Gson();
  private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private File todayFile(File dir) {
    return new File(dir, "decisions-" + FILE_DATE_FORMAT.format(LocalDate.now(ZoneOffset.UTC)) + ".json");
  }

  private JsonObject onlyEntry(File dir) throws IOException {
    List<String> lines = Files.readAllLines(todayFile(dir).toPath());
    assertEquals(1, lines.size());
    return GSON.fromJson(lines.get(0), JsonObject.class);
  }

  @Test
  public void doesNothingWhenRetentionIsZero() {
    File dir = new File(tmp.getRoot(), "logs");
    DecisionLog.recordRuleEvaluation(dir, 0, DecisionLog.Trigger.INBOX, null, "Subject", "alice@example.com", 0, List.of());
    assertFalse("PII-carrying by design opt-in: a retention of 0 must create nothing at all", dir.isDirectory());
  }

  @Test
  public void doesNothingWhenDirIsNull() {
    // Mirrors RuleContext.EMPTY (no account context available) — must not throw.
    DecisionLog.recordRuleEvaluation(null, 10, DecisionLog.Trigger.INBOX, null, "Subject", "alice@example.com", 0, List.of());
  }

  @Test
  public void recordsOneLinePerMessageWithEveryRuleReached() throws IOException {
    File dir = new File(tmp.getRoot(), "logs");
    List<DecisionLog.RuleOutcome> rules = List.of(
        new DecisionLog.RuleOutcome("Rule(FROM_DOMAIN_EQUALS(spam.com),MOVE_TO(Spam))", false, false, null, null, null),
        new DecisionLog.RuleOutcome("Rule(FROM_ADDRESS_EQUALS(alice@example.com),READ())", false, true,
            "FromAddressMatcher(alice@example.com)", false, null)
    );

    DecisionLog.recordRuleEvaluation(dir, 10, DecisionLog.Trigger.INBOX, "<abc@example.com>", "Hello", "alice@example.com", 42, rules);

    JsonObject entry = onlyEntry(dir);
    assertEquals("INBOX", entry.get("trigger").getAsString());
    assertEquals("<abc@example.com>", entry.get("messageId").getAsString());
    assertEquals(42, entry.get("processedMs").getAsLong());
    assertEquals("Hello", entry.get("subject").getAsString());
    assertEquals("alice@example.com", entry.get("from").getAsString());
    assertEquals(2, entry.getAsJsonArray("rules").size());
    JsonObject secondRule = entry.getAsJsonArray("rules").get(1).getAsJsonObject();
    assertTrue(secondRule.get("matched").getAsBoolean());
    assertEquals("FromAddressMatcher(alice@example.com)", secondRule.get("matchDetail").getAsString());
    assertFalse("no matchDetail on a non-match", entry.getAsJsonArray("rules").get(0).getAsJsonObject().has("matchDetail"));
    assertFalse(secondRule.get("keepProcessing").getAsBoolean());
    assertFalse("an INBOX/MANUAL_REPROCESSING entry has no learning-specific fields", entry.has("learnedRule"));
    assertFalse(entry.has("reasonForMoveToDone"));
  }

  @Test
  public void omitsMatchedButKeepsTheExceptionWhenARuleThrew() throws IOException {
    File dir = new File(tmp.getRoot(), "logs");
    List<DecisionLog.RuleOutcome> rules = List.of(
        new DecisionLog.RuleOutcome("Rule(...)", false, null, null, null, "java.lang.RuntimeException: boom")
    );

    DecisionLog.recordRuleEvaluation(dir, 10, DecisionLog.Trigger.INBOX, null, "Subject", "bob@example.com", 0, rules);

    JsonObject rule = onlyEntry(dir).getAsJsonArray("rules").get(0).getAsJsonObject();
    assertFalse("matched must be omitted (not false) when the rule never got to evaluate", rule.has("matched"));
    assertEquals("java.lang.RuntimeException: boom", rule.get("exception").getAsString());
  }

  @Test
  public void recordsALearningSuccessWithNoReasonForMoveToDone() throws IOException {
    File dir = new File(tmp.getRoot(), "logs");

    DecisionLog.recordLearningOutcome(dir, 10, null, "Subject", "spammer@example.com",
        "FROM_DOMAIN_EQUALS(example.com) -> MOVE_TO(Spam)", null);

    JsonObject entry = onlyEntry(dir);
    assertEquals("LEARNING", entry.get("trigger").getAsString());
    assertEquals("FROM_DOMAIN_EQUALS(example.com) -> MOVE_TO(Spam)", entry.get("learnedRule").getAsString());
    assertFalse(entry.has("reasonForMoveToDone"));
    assertFalse("a LEARNING entry has no rules array", entry.has("rules"));
    assertFalse("nor a processing time", entry.has("processedMs"));
    assertFalse("no Message-ID known: omitted", entry.has("messageId"));
  }

  @Test
  public void recordsALearningFailureWithReasonForMoveToDoneAndNoLearnedRule() throws IOException {
    File dir = new File(tmp.getRoot(), "logs");

    DecisionLog.recordLearningOutcome(dir, 10, null, "Subject", "(unknown)", null, "could not learn a rule: no valid From address");

    JsonObject entry = onlyEntry(dir);
    assertFalse(entry.has("learnedRule"));
    assertEquals("could not learn a rule: no valid From address", entry.get("reasonForMoveToDone").getAsString());
  }

  @Test
  public void aSuccessfulLearnWhoseActionDidNotRelocateTheMessageCanSetBothFields() throws IOException {
    File dir = new File(tmp.getRoot(), "logs");

    DecisionLog.recordLearningOutcome(dir, 10, null, "Subject", "alice@example.com",
        "FROM_EQUALS(alice@example.com) -> NOOP()", "learned successfully, but its action didn't relocate the message");

    JsonObject entry = onlyEntry(dir);
    assertEquals("FROM_EQUALS(alice@example.com) -> NOOP()", entry.get("learnedRule").getAsString());
    assertEquals("learned successfully, but its action didn't relocate the message", entry.get("reasonForMoveToDone").getAsString());
  }

  @Test
  public void rotatesOnTheFirstWriteOfANewDay() throws IOException {
    File dir = new File(tmp.getRoot(), "logs");
    Files.createDirectories(dir.toPath());

    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    File yesterdayFile = new File(dir, "decisions-" + FILE_DATE_FORMAT.format(today.minusDays(1)) + ".json");
    // retentionDays=3: the file dated exactly 3 days ago is itself the one that must go.
    File atTheBoundaryFile = new File(dir, "decisions-" + FILE_DATE_FORMAT.format(today.minusDays(3)) + ".json");
    Files.writeString(yesterdayFile.toPath(), "{\"yesterday\":true}\n");
    Files.writeString(atTheBoundaryFile.toPath(), "{\"expired\":true}\n");

    DecisionLog.recordRuleEvaluation(dir, 3, DecisionLog.Trigger.INBOX, null, "Subject", "alice@example.com", 0, List.of());

    assertFalse("yesterday's plain file must be gone, compressed instead", yesterdayFile.isFile());
    assertTrue("yesterday's file must have been compressed", new File(yesterdayFile.getAbsolutePath() + ".lz4").isFile());
    assertEquals("{\"yesterday\":true}", decompress(new File(yesterdayFile.getAbsolutePath() + ".lz4")).trim());

    assertFalse("the file dated exactly retentionDays ago must be deleted outright, not compressed",
        atTheBoundaryFile.isFile());
    assertFalse(new File(atTheBoundaryFile.getAbsolutePath() + ".lz4").isFile());

    assertTrue("today's new file must exist", todayFile(dir).isFile());
  }

  @Test
  public void doesNotRotateAgainOnASecondWriteTheSameDay() throws IOException {
    File dir = new File(tmp.getRoot(), "logs");

    DecisionLog.recordRuleEvaluation(dir, 10, DecisionLog.Trigger.INBOX, null, "First", "alice@example.com", 0, List.of());
    DecisionLog.recordRuleEvaluation(dir, 10, DecisionLog.Trigger.INBOX, null, "Second", "alice@example.com", 0, List.of());

    List<String> lines = Files.readAllLines(todayFile(dir).toPath());
    assertEquals("both writes must append to the same file, not rotate between them", 2, lines.size());
  }

  private static String decompress(File lz4File) throws IOException {
    try (LZ4FrameInputStream in = new LZ4FrameInputStream(Files.newInputStream(lz4File.toPath()))) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
