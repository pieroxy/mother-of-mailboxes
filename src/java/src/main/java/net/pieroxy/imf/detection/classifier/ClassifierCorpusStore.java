package net.pieroxy.imf.detection.classifier;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import net.jpountz.lz4.LZ4FrameInputStream;
import net.jpountz.lz4.LZ4FrameOutputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stores the classifier corpus in an lz4-compressed JSON file per day
 * (classifier-YYYY-MM-DD.json.lz4), one folder per account. Merges with the day's file if it
 * already exists (resuming after an interruption mid-day) and writes atomically (temp file then
 * rename), like {@code LogRotator}, so an archive is never left truncated on an abrupt outage.
 * keepDays&lt;=0 disables pruning of old archives.
 */
public class ClassifierCorpusStore {
  private final static Logger LOGGER = Logger.getLogger(ClassifierCorpusStore.class.getName());
  private final static Gson GSON = new Gson();
  private final static Type LIST_TYPE = new TypeToken<List<ClassifierExample>>() {}.getType();
  private final static Pattern FILE_DATE_PATTERN = Pattern.compile("classifier-(\\d{4}-\\d{2}-\\d{2})\\.json\\.lz4");

  private final File accountFolder;
  private final int keepDays;
  private final String logPrefix;

  public ClassifierCorpusStore(String dataFolder, String accountKey, int keepDays) {
    this.accountFolder = new File(new File(dataFolder, "classifier-corpus"), accountKey);
    this.keepDays = keepDays;
    this.logPrefix = "Classifier corpus [" + accountKey + "] ";
  }

  public void append(LocalDate day, List<ClassifierExample> newExamples) throws IOException {
    if (newExamples.isEmpty()) return;
    accountFolder.mkdirs();
    File file = fileFor(day);
    List<ClassifierExample> all = new ArrayList<>(read(file));
    all.addAll(newExamples);
    write(file, all);
  }

  /**
   * All examples currently retained (so already within the retention window if pruneOlderThan
   * was called beforehand), across all daily files, deduplicated by Message-ID (see
   * {@link #dedupeByMessageId}). Used by {@link SubjectClassifierTrainer} to (re)train on
   * everything that's retained.
   */
  public List<ClassifierExample> readAll() {
    File[] files = accountFolder.listFiles();
    if (files == null) return List.of();
    List<ClassifierExample> all = new ArrayList<>();
    for (File f : files) {
      if (FILE_DATE_PATTERN.matcher(f.getName()).matches()) {
        all.addAll(read(f));
      }
    }
    return dedupeByMessageId(all);
  }

  /**
   * The same message can be captured twice with contradictory labels: auto-classified SPAM by
   * the (fast, every-cycle) Spam folder scan, then moved out by hand by a user who judges it
   * legitimate — seen again, this time HAM, by the full daily scan that follows. An IMAP move
   * gives the message a new UID in the destination folder (so per-folder UID tracking doesn't
   * see "the same message"), so nothing upstream prevents this double capture — fixing it here,
   * at read time, is simpler than preventing it at scan time (no need to re-read the whole
   * existing corpus on every append). For a Message-ID seen several times, only the example with
   * the most recent fetchDate (i.e. the most up-to-date verdict, the one reflecting where the
   * user ended up filing the message) is kept — otherwise the two would cancel each other out
   * instead of teaching anything. Examples without a Message-ID (rare, malformed mail) can't be
   * reliably matched up: kept as is, never deduplicated against each other.
   */
  private static List<ClassifierExample> dedupeByMessageId(List<ClassifierExample> examples) {
    Map<String, ClassifierExample> latestById = new LinkedHashMap<>();
    List<ClassifierExample> withoutId = new ArrayList<>();
    for (ClassifierExample example : examples) {
      String id = example.getMessageId();
      if (id == null) {
        withoutId.add(example);
        continue;
      }
      ClassifierExample existing = latestById.get(id);
      if (existing == null || isAfter(example.getFetchDate(), existing.getFetchDate())) {
        latestById.put(id, example);
      }
    }
    List<ClassifierExample> result = new ArrayList<>(latestById.values());
    result.addAll(withoutId);
    return result;
  }

  /** fetchDate is formatted as ISO-8601 UTC (DateTimeFormatter.ISO_INSTANT): lexicographically comparable. */
  private static boolean isAfter(String a, String b) {
    if (a == null) return false;
    if (b == null) return true;
    return a.compareTo(b) > 0;
  }

  /** Path to the trained subject classification model for this account. */
  public File getModelFile() {
    return new File(accountFolder, "subject-model.bin");
  }

  /** Path to the trained header classification model for this account (see {@code HeaderClassifierTrainer}) — separate file, separate model from the subject one above. */
  public File getHeaderModelFile() {
    return new File(accountFolder, "header-model.bin");
  }

  /** Path to the trained body classification model for this account (see {@code BodyClassifierTrainer}) — separate file, separate model from the two above. */
  public File getBodyModelFile() {
    return new File(accountFolder, "body-model.bin");
  }

  /** Deletes files dated more than keepDays days before today. No effect if keepDays<=0. */
  public void pruneOlderThan(LocalDate today) {
    if (keepDays <= 0) return;
    File[] files = accountFolder.listFiles();
    if (files == null) return;
    LocalDate cutoff = today.minusDays(keepDays);
    for (File f : files) {
      Matcher m = FILE_DATE_PATTERN.matcher(f.getName());
      if (!m.matches()) continue;
      LocalDate fileDate = LocalDate.parse(m.group(1));
      if (fileDate.isBefore(cutoff) && !f.delete()) {
        LOGGER.warning(logPrefix + "Could not delete stale corpus file " + f);
      }
    }
  }

  private File fileFor(LocalDate day) {
    return new File(accountFolder, "classifier-" + day + ".json.lz4");
  }

  private List<ClassifierExample> read(File file) {
    if (!file.isFile()) return List.of();
    try (Reader r = new InputStreamReader(new LZ4FrameInputStream(new FileInputStream(file)))) {
      List<ClassifierExample> examples = GSON.fromJson(r, LIST_TYPE);
      return examples != null ? examples : List.of();
    } catch (IOException | JsonParseException e) {
      LOGGER.log(Level.WARNING, logPrefix + "Could not read corpus file " + file, e);
      return List.of();
    }
  }

  private void write(File file, List<ClassifierExample> examples) throws IOException {
    File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
    try (Writer w = new OutputStreamWriter(new LZ4FrameOutputStream(new FileOutputStream(tmp)))) {
      GSON.toJson(examples, LIST_TYPE, w);
    }
    Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
  }
}
