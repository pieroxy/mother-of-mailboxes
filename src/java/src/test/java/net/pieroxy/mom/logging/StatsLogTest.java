package net.pieroxy.mom.logging;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.pieroxy.mom.utils.logging.StatsLog;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StatsLogTest {
  private static final Gson GSON = new Gson();
  private static final Pattern DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}Z");

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  @Test
  public void doesNothingWhenStatsDirIsNull() {
    // RuleContext.EMPTY (used by most matcher/rule unit tests) has no stats dir — must be a
    // silent no-op, not an NPE or a stray file somewhere.
    StatsLog.recordMatch(null, "FromDomainMatcher(gmail.com)");
  }

  @Test
  public void writesOneJsonLinePerEventToTodaysFile() throws IOException {
    File statsDir = new File(tmp.getRoot(), "logs");

    StatsLog.recordMatch(statsDir, "IpReputationMatcher[spamhaus-drop](score=0.87)");

    String today = LocalDate.now(ZoneOffset.UTC).toString();
    File expected = new File(statsDir, "stats-" + today + ".json");
    assertTrue(expected.isFile());

    List<String> lines = Files.readAllLines(expected.toPath());
    assertEquals(1, lines.size());

    JsonObject event = GSON.fromJson(lines.get(0), JsonObject.class);
    assertEquals("MATCH", event.get("type").getAsString());
    assertEquals("IpReputationMatcher[spamhaus-drop](score=0.87)", event.get("matcher").getAsString());
    assertTrue("date should look like yyyy-MM-dd HH:mm:ssZ, was: " + event.get("date").getAsString(),
        DATE_PATTERN.matcher(event.get("date").getAsString()).matches());
  }

  @Test
  public void recordProcessedWritesTypeResultMatcherAndDuration() throws IOException {
    File statsDir = new File(tmp.getRoot(), "logs");

    StatsLog.recordProcessed(statsDir, StatsLog.ProcessResult.MATCH, "FromDomainMatcher(gmail.com)", 42);

    String today = LocalDate.now(ZoneOffset.UTC).toString();
    List<String> lines = Files.readAllLines(new File(statsDir, "stats-" + today + ".json").toPath());
    assertEquals(1, lines.size());

    JsonObject event = GSON.fromJson(lines.get(0), JsonObject.class);
    assertEquals("PROCESSED", event.get("type").getAsString());
    assertEquals("MATCH", event.get("result").getAsString());
    assertEquals("FromDomainMatcher(gmail.com)", event.get("matcher").getAsString());
    assertEquals(42, event.get("processedMs").getAsLong());
  }

  @Test
  public void recordProcessedSupportsPassResultWithNoMatcher() throws IOException {
    File statsDir = new File(tmp.getRoot(), "logs");

    StatsLog.recordProcessed(statsDir, StatsLog.ProcessResult.PASS, null, 7);

    String today = LocalDate.now(ZoneOffset.UTC).toString();
    List<String> lines = Files.readAllLines(new File(statsDir, "stats-" + today + ".json").toPath());
    JsonObject event = GSON.fromJson(lines.get(0), JsonObject.class);
    assertEquals("PASS", event.get("result").getAsString());
    assertFalse("a PASS event has no matcher field", event.has("matcher"));
  }

  @Test
  public void doesNothingForRecordProcessedWhenStatsDirIsNull() {
    StatsLog.recordProcessed(null, StatsLog.ProcessResult.PASS, null, 5);
  }

  @Test
  public void appendsRatherThanOverwritingOnSubsequentCalls() throws IOException {
    File statsDir = new File(tmp.getRoot(), "logs");

    StatsLog.recordMatch(statsDir, "FromDomainMatcher(gmail.com)");
    StatsLog.recordMatch(statsDir, "FromExactMatcher(alice@example.com)");

    String today = LocalDate.now(ZoneOffset.UTC).toString();
    List<String> lines = Files.readAllLines(new File(statsDir, "stats-" + today + ".json").toPath());

    assertEquals(2, lines.size());
    assertEquals("FromDomainMatcher(gmail.com)", GSON.fromJson(lines.get(0), JsonObject.class).get("matcher").getAsString());
    assertEquals("FromExactMatcher(alice@example.com)", GSON.fromJson(lines.get(1), JsonObject.class).get("matcher").getAsString());
  }

  @Test
  public void createsTheStatsDirectoryWhenMissing() {
    File statsDir = new File(tmp.getRoot(), "does/not/exist/yet");
    assertFalse(statsDir.isDirectory());

    StatsLog.recordMatch(statsDir, "FromDomainMatcher(gmail.com)");

    assertTrue(statsDir.isDirectory());
  }
}
