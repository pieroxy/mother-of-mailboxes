package net.pieroxy.mom.logging;

import net.pieroxy.mom.utils.logging.StatsLog;
import net.pieroxy.mom.utils.logging.StatsReader;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class StatsReaderTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private File statsFile(LocalDate day) {
    return new File(tmp.getRoot(), "stats-" + day + ".json");
  }

  private void writeLine(LocalDate day, String json) throws IOException {
    try (FileWriter w = new FileWriter(statsFile(day), true)) {
      w.write(json);
      w.write(System.lineSeparator());
    }
  }

  @Test
  public void aDayWithNoFileContributesAllZeroesRatherThanAGap() {
    LocalDate day = LocalDate.of(2026, 1, 1);

    StatsReader.StatsRange range = StatsReader.read(tmp.getRoot(), day, day);

    assertEquals(1, range.days().size());
    StatsReader.DailyStats stats = range.days().get(0);
    assertEquals(day, stats.date());
    assertEquals(0, stats.messagesProcessed());
    assertEquals(0, stats.messagesMatched());
    assertEquals(0.0, stats.avgProcessingMs(), 0.0001);
  }

  @Test
  public void aggregatesProcessedAndMatchedCountsForOneDay() {
    // StatsLog always stamps its file with today's UTC date (see StatsLog#write) — not
    // controllable from the caller, so this exercises today's file rather than a fixed date.
    LocalDate day = LocalDate.now(ZoneOffset.UTC);
    File statsDir = tmp.getRoot();
    StatsLog.recordProcessed(statsDir, StatsLog.ProcessResult.MATCH, "FromDomainMatcher(spam.example.com)", 10);
    StatsLog.recordProcessed(statsDir, StatsLog.ProcessResult.PASS, null, 20);
    StatsLog.recordProcessed(statsDir, StatsLog.ProcessResult.PASS, null, 30);

    StatsReader.StatsRange range = StatsReader.read(statsDir, day, day);

    StatsReader.DailyStats stats = range.days().get(0);
    assertEquals(3, stats.messagesProcessed());
    assertEquals(1, stats.messagesMatched());
    assertEquals(20.0, stats.avgProcessingMs(), 0.0001); // (10 + 20 + 30) / 3
  }

  @Test
  public void countsHowManyTimesEachMatcherFiredAcrossTheWholeRange() {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    File statsDir = tmp.getRoot();
    StatsLog.recordMatch(statsDir, "FromDomainMatcher(spam.example.com)");
    StatsLog.recordMatch(statsDir, "FromDomainMatcher(spam.example.com)");
    StatsLog.recordMatch(statsDir, "SubjectContainsMatcher(free money)");

    // Spans today plus a neighboring, file-less day — the range covers both, only today has data.
    StatsReader.StatsRange range = StatsReader.read(statsDir, today.minusDays(1), today);

    Map<String, Long> matcherCounts = range.matcherCounts();
    assertEquals(2L, (long) matcherCounts.get("FromDomainMatcher(spam.example.com)"));
    assertEquals(1L, (long) matcherCounts.get("SubjectContainsMatcher(free money)"));
  }

  @Test
  public void groupsMatchersThatOnlyDifferByTheirScoreValue() {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    File statsDir = tmp.getRoot();
    StatsLog.recordMatch(statsDir, "BodyClassifierMatcher(score=1.0)");
    StatsLog.recordMatch(statsDir, "BodyClassifierMatcher(score=0.999)");
    StatsLog.recordMatch(statsDir, "IpReputationMatcher[spamhaus-drop](score=0.87)");
    StatsLog.recordMatch(statsDir, "IpReputationMatcher[spamhaus-drop](score=0.42)");

    Map<String, Long> matcherCounts = StatsReader.read(statsDir, today, today).matcherCounts();

    assertEquals(2L, (long) matcherCounts.get("BodyClassifierMatcher(score)"));
    assertEquals(2L, (long) matcherCounts.get("IpReputationMatcher[spamhaus-drop](score)"));
  }

  @Test
  public void coversEveryDayInTheRangeInOrder() {
    LocalDate from = LocalDate.of(2026, 3, 1);
    LocalDate to = LocalDate.of(2026, 3, 4);

    List<StatsReader.DailyStats> days = StatsReader.read(tmp.getRoot(), from, to).days();

    assertEquals(4, days.size());
    assertEquals(LocalDate.of(2026, 3, 1), days.get(0).date());
    assertEquals(LocalDate.of(2026, 3, 2), days.get(1).date());
    assertEquals(LocalDate.of(2026, 3, 3), days.get(2).date());
    assertEquals(LocalDate.of(2026, 3, 4), days.get(3).date());
  }

  @Test
  public void skipsAMalformedLineButStillReadsTheRestOfTheDay() throws IOException {
    LocalDate day = LocalDate.now(ZoneOffset.UTC);
    File statsDir = tmp.getRoot();
    writeLine(day, "{ this is not valid json");
    StatsLog.recordProcessed(statsDir, StatsLog.ProcessResult.MATCH, "FromDomainMatcher(spam.example.com)", 5);

    StatsReader.StatsRange range = StatsReader.read(statsDir, day, day);

    StatsReader.DailyStats stats = range.days().get(0);
    assertEquals("the well-formed line after the corrupt one must still be counted", 1, stats.messagesProcessed());
    assertEquals(1, stats.messagesMatched());
  }
}
