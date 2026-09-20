package net.pieroxy.imf.detection.classifier;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.jpountz.lz4.LZ4FrameInputStream;
import net.pieroxy.imf.detection.classifier.ClassifierCorpusStore;
import net.pieroxy.imf.detection.classifier.ClassifierExample;
import net.pieroxy.imf.detection.classifier.ClassifierLabel;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClassifierCorpusStoreTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private static ClassifierExample example(String subject, ClassifierLabel label) {
    ClassifierExample e = new ClassifierExample();
    e.setSubject(subject);
    e.setLabel(label);
    e.setFrom(Collections.emptyList());
    e.setTo(Collections.emptyList());
    return e;
  }

  private static ClassifierExample example(String subject, ClassifierLabel label, String messageId, String fetchDate) {
    ClassifierExample e = example(subject, label);
    e.setMessageId(messageId);
    e.setFetchDate(fetchDate);
    return e;
  }

  private File dayFile(String accountKey, LocalDate day) {
    return new File(new File(new File(tmp.getRoot(), "classifier-corpus"), accountKey),
        "classifier-" + day + ".json.lz4");
  }

  @Test
  public void appendCreatesACompressedFileForTheDay() throws Exception {
    ClassifierCorpusStore store = new ClassifierCorpusStore(tmp.getRoot().getAbsolutePath(), "account", 30);
    LocalDate day = LocalDate.of(2026, 9, 30);

    store.append(day, Arrays.asList(example("Buy now", ClassifierLabel.SPAM)));

    assertTrue(dayFile("account", day).isFile());
  }

  @Test
  public void appendMergesWithExistingDayFile() throws Exception {
    ClassifierCorpusStore store = new ClassifierCorpusStore(tmp.getRoot().getAbsolutePath(), "account", 30);
    LocalDate day = LocalDate.of(2026, 9, 30);

    store.append(day, Arrays.asList(example("first", ClassifierLabel.HAM)));
    store.append(day, Arrays.asList(example("second", ClassifierLabel.SPAM)));

    List<ClassifierExample> all = readCompressedFile(dayFile("account", day));
    assertEquals(2, all.size());
  }

  @Test
  public void appendDoesNothingForAnEmptyList() throws Exception {
    ClassifierCorpusStore store = new ClassifierCorpusStore(tmp.getRoot().getAbsolutePath(), "account", 30);

    store.append(LocalDate.of(2026, 9, 30), Collections.emptyList());

    assertFalse(dayFile("account", LocalDate.of(2026, 9, 30)).exists());
  }

  @Test
  public void pruneRemovesFilesOlderThanKeepDaysButKeepsRecentOnes() throws Exception {
    ClassifierCorpusStore store = new ClassifierCorpusStore(tmp.getRoot().getAbsolutePath(), "account", 7);
    LocalDate today = LocalDate.of(2026, 9, 30);
    LocalDate old = today.minusDays(10);
    LocalDate recent = today.minusDays(2);
    store.append(old, Arrays.asList(example("old", ClassifierLabel.HAM)));
    store.append(recent, Arrays.asList(example("recent", ClassifierLabel.HAM)));

    store.pruneOlderThan(today);

    assertFalse(dayFile("account", old).exists());
    assertTrue(dayFile("account", recent).exists());
  }

  @Test
  public void pruneDoesNothingWhenKeepDaysIsZeroOrNegative() throws Exception {
    ClassifierCorpusStore store = new ClassifierCorpusStore(tmp.getRoot().getAbsolutePath(), "account", 0);
    LocalDate old = LocalDate.of(2000, 1, 1);
    store.append(old, Arrays.asList(example("old", ClassifierLabel.HAM)));

    store.pruneOlderThan(LocalDate.of(2026, 9, 30));

    assertTrue(dayFile("account", old).exists());
  }

  @Test
  public void readAllReturnsExamplesAcrossMultipleDayFiles() throws Exception {
    ClassifierCorpusStore store = new ClassifierCorpusStore(tmp.getRoot().getAbsolutePath(), "account", 30);
    store.append(LocalDate.of(2026, 9, 29), Arrays.asList(example("first", ClassifierLabel.HAM)));
    store.append(LocalDate.of(2026, 9, 30), Arrays.asList(example("second", ClassifierLabel.SPAM)));

    assertEquals(2, store.readAll().size());
  }

  /**
   * The real-world scenario: a message auto-labeled SPAM by the (fast) Spam folder scan, then
   * moved out by hand by the user who judges it legitimate — recaptured, this time HAM, by the
   * next full daily scan. An IMAP move changes the message's UID, so nothing prevents this
   * double capture with the same Message-ID but contradictory labels; readAll() must keep only
   * the most recent verdict (the latest fetchDate).
   */
  @Test
  public void readAllKeepsOnlyTheLatestVerdictForTheSameMessageId() throws Exception {
    ClassifierCorpusStore store = new ClassifierCorpusStore(tmp.getRoot().getAbsolutePath(), "account", 30);
    store.append(LocalDate.of(2026, 9, 29),
        Arrays.asList(example("Hello", ClassifierLabel.SPAM, "<msg-1@example.com>", "2026-09-29T08:00:00Z")));
    store.append(LocalDate.of(2026, 9, 30),
        Arrays.asList(example("Hello", ClassifierLabel.HAM, "<msg-1@example.com>", "2026-09-30T08:00:00Z")));

    List<ClassifierExample> all = store.readAll();

    assertEquals(1, all.size());
    assertEquals(ClassifierLabel.HAM, all.get(0).getLabel());
  }

  @Test
  public void readAllKeepsTheEarlierExampleIfItsFetchDateIsActuallyLater() throws Exception {
    // Checks that it's really fetchDate that decides, not the order files are read in.
    ClassifierCorpusStore store = new ClassifierCorpusStore(tmp.getRoot().getAbsolutePath(), "account", 30);
    store.append(LocalDate.of(2026, 9, 29),
        Arrays.asList(example("Hello", ClassifierLabel.HAM, "<msg-1@example.com>", "2026-09-30T08:00:00Z")));
    store.append(LocalDate.of(2026, 9, 30),
        Arrays.asList(example("Hello", ClassifierLabel.SPAM, "<msg-1@example.com>", "2026-09-29T08:00:00Z")));

    List<ClassifierExample> all = store.readAll();

    assertEquals(1, all.size());
    assertEquals(ClassifierLabel.HAM, all.get(0).getLabel());
  }

  @Test
  public void readAllNeverDedupesExamplesWithoutAMessageId() throws Exception {
    ClassifierCorpusStore store = new ClassifierCorpusStore(tmp.getRoot().getAbsolutePath(), "account", 30);
    store.append(LocalDate.of(2026, 9, 30), Arrays.asList(
        example("first", ClassifierLabel.SPAM), example("second", ClassifierLabel.HAM)));

    assertEquals(2, store.readAll().size());
  }

  /** Reads the compressed file directly: checks the format actually persisted to disk. */
  private List<ClassifierExample> readCompressedFile(File file) throws Exception {
    try (LZ4FrameInputStream in = new LZ4FrameInputStream(new FileInputStream(file))) {
      String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      Type type = new TypeToken<List<ClassifierExample>>() {}.getType();
      return new Gson().fromJson(json, type);
    }
  }
}
