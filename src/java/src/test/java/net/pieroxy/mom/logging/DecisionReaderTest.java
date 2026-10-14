package net.pieroxy.mom.logging;

import com.google.gson.Gson;
import net.jpountz.lz4.LZ4FrameOutputStream;
import net.pieroxy.mom.utils.logging.DecisionLog;
import net.pieroxy.mom.utils.logging.DecisionReader;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DecisionReaderTest {
  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private static final Gson GSON = new Gson();
  private static final LocalDate DAY1 = LocalDate.of(2026, 10, 1);
  private static final LocalDate DAY2 = LocalDate.of(2026, 10, 2);
  private static final LocalDate DAY3 = LocalDate.of(2026, 10, 3);
  private static final String SPAM_RULE = "Rule(FROM_DOMAIN_EQUALS(spam.com),MOVE_TO(Spam))";

  private static DecisionLog.Entry inbox(String subject, DecisionLog.RuleOutcome... rules) {
    return new DecisionLog.Entry("2026-10-01 10:00:00Z", "INBOX", "<" + subject + "@x>", subject, "alice@example.com", 5L,
        List.of(rules), null, null);
  }

  private static DecisionLog.Entry learning(String subject, String learnedRule) {
    return new DecisionLog.Entry("2026-10-01 10:00:00Z", "LEARNING", null, subject, "bob@example.com", null,
        null, learnedRule, learnedRule == null ? "could not learn a rule" : null);
  }

  private static DecisionLog.RuleOutcome blocked() {
    return new DecisionLog.RuleOutcome(SPAM_RULE, false, true, "FromDomainMatcher(spam.com)", "→ Spam", true, false, null);
  }

  private static DecisionLog.RuleOutcome noMatch() {
    return new DecisionLog.RuleOutcome(SPAM_RULE, false, false, null, null, null, null, null);
  }

  private static DecisionLog.RuleOutcome crashed() {
    return new DecisionLog.RuleOutcome("Rule(...)", false, null, null, null, null, null, "java.lang.RuntimeException: boom");
  }

  private File dir() {
    return new File(tmp.getRoot(), "logs");
  }

  private void writeDay(LocalDate day, boolean compressed, DecisionLog.Entry... entries) throws IOException {
    StringBuilder content = new StringBuilder();
    for (DecisionLog.Entry e : entries) content.append(GSON.toJson(e)).append('\n');
    Files.createDirectories(dir().toPath());
    File file = new File(dir(), "decisions-" + day + ".json" + (compressed ? ".lz4" : ""));
    try (OutputStream out = compressed ? new LZ4FrameOutputStream(new FileOutputStream(file)) : new FileOutputStream(file)) {
      out.write(content.toString().getBytes(StandardCharsets.UTF_8));
    }
  }

  private static List<String> subjects(DecisionReader.Page page) {
    return page.entries().stream().map(DecisionLog.Entry::subject).toList();
  }

  private DecisionReader.Page read(DecisionReader.Filter filter) {
    return DecisionReader.read(dir(), DAY1, DAY3, filter, null, 100, 1000);
  }

  @Test
  public void readsNewestFirstAcrossPlainAndCompressedDays() throws IOException {
    writeDay(DAY1, true, inbox("a"), inbox("b"));
    writeDay(DAY3, false, inbox("c"), inbox("d"));

    DecisionReader.Page page = read(DecisionReader.Filter.NONE);

    assertEquals(List.of("d", "c", "b", "a"), subjects(page));
    assertNull(page.nextCursor());
  }

  @Test
  public void missingDirectoryIsEmpty() {
    DecisionReader.Page page = read(DecisionReader.Filter.NONE);
    assertTrue(page.entries().isEmpty());
    assertNull(page.nextCursor());
  }

  @Test
  public void pagesFollowEachOtherWithoutGapsOrDuplicates() throws IOException {
    writeDay(DAY1, true, inbox("a"), inbox("b"), inbox("c"));
    writeDay(DAY2, false, inbox("d"));
    writeDay(DAY3, false, inbox("e"), inbox("f"));

    List<String> all = new ArrayList<>();
    String cursor = null;
    int pages = 0;
    do {
      DecisionReader.Page page = DecisionReader.read(dir(), DAY1, DAY3, DecisionReader.Filter.NONE, cursor, 2, 1000);
      all.addAll(subjects(page));
      cursor = page.nextCursor();
      pages++;
    } while (cursor != null);

    assertEquals(List.of("f", "e", "d", "c", "b", "a"), all);
    assertEquals(3, pages);
  }

  @Test
  public void aCursorStaysValidWhileTodaysFileGrows() throws IOException {
    writeDay(DAY3, false, inbox("a"), inbox("b"), inbox("c"));
    DecisionReader.Page first = DecisionReader.read(dir(), DAY1, DAY3, DecisionReader.Filter.NONE, null, 2, 1000);
    assertEquals(List.of("c", "b"), subjects(first));

    writeDay(DAY3, false, inbox("a"), inbox("b"), inbox("c"), inbox("new"));
    DecisionReader.Page second = DecisionReader.read(dir(), DAY1, DAY3, DecisionReader.Filter.NONE, first.nextCursor(), 2, 1000);

    assertEquals(List.of("a"), subjects(second));
  }

  @Test
  public void scanBudgetStopsEarlyWithACursor() throws IOException {
    writeDay(DAY3, false, inbox("a"), inbox("b"), inbox("c"), inbox("d"));
    DecisionReader.Filter onlyA = new DecisionReader.Filter(null, null, false, null, null, "<a@");

    DecisionReader.Page page = DecisionReader.read(dir(), DAY1, DAY3, onlyA, null, 100, 2);
    assertTrue("budget spent on d and c, both filtered out", page.entries().isEmpty());
    assertNotNull(page.nextCursor());

    DecisionReader.Page next = DecisionReader.read(dir(), DAY1, DAY3, onlyA, page.nextCursor(), 100, 2);
    assertEquals(List.of("a"), subjects(next));
  }

  @Test
  public void derivesOutcomes() {
    assertEquals(DecisionReader.Outcome.HANDLED, DecisionReader.outcomeOf(inbox("x", noMatch(), blocked())));
    assertEquals(SPAM_RULE, DecisionReader.blockingRuleOf(inbox("x", noMatch(), blocked())).rule());
    assertEquals(DecisionReader.Outcome.NOT_HANDLED, DecisionReader.outcomeOf(inbox("x", noMatch())));
    DecisionLog.RuleOutcome kept = new DecisionLog.RuleOutcome(SPAM_RULE, false, true, "d", "→ Spam", true, true, null);
    assertEquals("a keepProcessing match doesn't block", DecisionReader.Outcome.NOT_HANDLED, DecisionReader.outcomeOf(inbox("x", kept)));
    assertEquals(DecisionReader.Outcome.LEARNED, DecisionReader.outcomeOf(learning("x", "FROM_EQUALS(a) -> NOOP()")));
    assertEquals(DecisionReader.Outcome.NOT_LEARNED, DecisionReader.outcomeOf(learning("x", null)));
  }

  @Test
  public void filtersByTriggerOutcomeErrorAndRule() throws IOException {
    writeDay(DAY2, false, inbox("blocked", blocked()), inbox("passed", noMatch()), inbox("crashed", crashed(), noMatch()),
        learning("learned", "FROM_EQUALS(a) -> NOOP()"));

    assertEquals(List.of("learned"), subjects(read(new DecisionReader.Filter(Set.of(DecisionLog.Trigger.LEARNING), null, false, null, null, null))));
    assertEquals(List.of("crashed", "passed"), subjects(read(new DecisionReader.Filter(null, Set.of(DecisionReader.Outcome.NOT_HANDLED), false, null, null, null))));
    assertEquals(List.of("crashed"), subjects(read(new DecisionReader.Filter(null, null, true, null, null, null))));
    assertEquals("only where the rule matched, not merely reached",
        List.of("blocked"), subjects(read(new DecisionReader.Filter(null, null, false, SPAM_RULE, null, null))));
  }

  @Test
  public void filtersByMatcherTheWayTheStatsPageKeysIt() throws IOException {
    DecisionLog.RuleOutcome scored = new DecisionLog.RuleOutcome("Rule(IP_REPUTATION_EQUALS(>0.5),MOVE_TO(Spam))", false, true,
        "IpReputationMatcher[spamhaus-drop](score=0.87)", "→ Spam", true, true, null);
    writeDay(DAY2, false, inbox("kept", scored, noMatch()), inbox("blocked", blocked()));

    assertEquals(List.of("kept"), subjects(read(matcher("IpReputationMatcher[spamhaus-drop](score)", false, false))));
    assertEquals(List.of("kept"), subjects(read(matcher("IpReputationMatcher[spamhaus-drop]", true, false))));
    assertEquals("a keepProcessing match never ended processing",
        List.of(), subjects(read(matcher("IpReputationMatcher[spamhaus-drop]", true, true))));
    assertEquals(List.of("blocked"), subjects(read(matcher("FromDomainMatcher(spam.com)", false, true))));
    assertEquals(List.of("blocked"), subjects(read(matcher("FromDomainMatcher", true, false))));
  }

  private static DecisionReader.Filter matcher(String key, boolean byType, boolean blockingOnly) {
    return new DecisionReader.Filter(null, null, false, null, new DecisionReader.MatcherFilter(key, byType, blockingOnly), null);
  }

  @Test
  public void queryIsCaseAndAccentInsensitiveOverSubjectFromAndMessageId() throws IOException {
    writeDay(DAY2, false, inbox("Réunion de Lundi"), inbox("other"));

    assertEquals(List.of("Réunion de Lundi"), subjects(read(new DecisionReader.Filter(null, null, false, null, null, "REUNION"))));
    assertEquals(List.of("other"), subjects(read(new DecisionReader.Filter(null, null, false, null, null, "<other@"))));
    assertEquals(2, read(new DecisionReader.Filter(null, null, false, null, null, "ALICE@")).entries().size());
  }

  @Test
  public void skipsMalformedLines() throws IOException {
    writeDay(DAY2, false, inbox("a"));
    Files.writeString(new File(dir(), "decisions-" + DAY2 + ".json").toPath(), "{truncated\n\n" + GSON.toJson(inbox("b")) + "\n",
        java.nio.file.StandardOpenOption.APPEND);

    assertEquals(List.of("b", "a"), subjects(read(DecisionReader.Filter.NONE)));
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsACursorOutsideTheRange() {
    DecisionReader.read(dir(), DAY1, DAY2, DecisionReader.Filter.NONE, DAY3 + "/1", 10, 100);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsAMalformedCursor() {
    DecisionReader.read(dir(), DAY1, DAY2, DecisionReader.Filter.NONE, "garbage", 10, 100);
  }
}
