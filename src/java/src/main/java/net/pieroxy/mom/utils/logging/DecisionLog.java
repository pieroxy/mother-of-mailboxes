package net.pieroxy.mom.utils.logging;

import com.google.gson.Gson;
import net.jpountz.lz4.LZ4FrameOutputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One JSON line per message processed, explaining *why* — every rule that was actually evaluated
 * and what it decided, or, for a message handled by the learning pathway, what was learned (or
 * why it was filed to {@code mom-rules/Done} instead). Appended to a per-UTC-day file
 * ({@code <statsDir>/decisions-yyyy-MM-dd.json}, same directory as {@link StatsLog}'s own files)
 * — deliberately a separate stream from {@code StatsLog}, not an extension of it: this one carries
 * the message's Subject and From, which {@code StatsLog} never has and never needs (it's pure
 * anonymous counts, kept forever). Because this one carries real mail metadata, it's opt-in and
 * short-lived by default — see {@link #write}.
 * <p>
 * One JSON object per line, same reasoning as {@code StatsLog}: an append is never a
 * read-modify-write of the whole day's file, so concurrent writers never corrupt each other.
 */
public final class DecisionLog {
  private final static Logger LOGGER = Logger.getLogger(DecisionLog.class.getName());
  private final static Gson GSON = new Gson();
  private final static DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private final static DateTimeFormatter EVENT_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  private final static String FILE_PREFIX = "decisions-";
  private final static Pattern DAY_FILE = Pattern.compile(Pattern.quote(FILE_PREFIX) + "(\\d{4}-\\d{2}-\\d{2})\\.json");

  /** Which code path produced a decision — see {@code RuleHelper#processRules}/{@code RuleLearner}. */
  public enum Trigger { INBOX, LEARNING, MANUAL_REPROCESSING }

  /**
   * One rule's outcome within a single message's {@code INBOX}/{@code MANUAL_REPROCESSING}
   * decision (see {@link #recordRuleEvaluation}) — one entry per rule actually reached, in
   * evaluation order; a rule after the one that blocked simply has no entry, since it never ran.
   * A {@code LEARNED_RULES} group (however many individual learned rules it holds) contributes
   * exactly one entry here, not one per learned rule — see {@code RuleHelper}.
   *
   * @param rule           {@code RuleInterface#describe()} — identifies the rule; no separate
   *                       name field exists yet, so this is always the auto-generated
   *                       {@code Rule(matcher,action)} form today.
   * @param matched        true/false on a clean evaluation; null if the matcher threw before a
   *                       match could even be determined (see {@code exception}) — a crashed rule
   *                       is deliberately not conflated with one that cleanly didn't match.
   * @param keepProcessing only present when matched is true: whether this match let evaluation
   *                       carry on to the next rule, or stopped it here.
   * @param exception      {@code toString()} of whatever was thrown, if anything — set whether or
   *                       not matched is true (a rule can match cleanly and then have its action
   *                       throw; matched stays true, and this explains what went wrong applying
   *                       the consequence).
   */
  public record RuleOutcome(String rule, Boolean matched, Boolean keepProcessing, String exception) {}

  private DecisionLog() {}

  /** One entry per message evaluated against the rule catalog — INBOX or MANUAL_REPROCESSING (see {@code RuleHelper#processRules}). */
  public static void recordRuleEvaluation(File decisionsDir, int retentionDays, Trigger trigger, String subject, String from, List<RuleOutcome> rules) {
    write(decisionsDir, retentionDays, trigger.name(), subject, from, rules, null, null);
  }

  /**
   * One entry per example message handled by the learning pathway (see {@code RuleLearner}):
   * {@code learnedRule} names the rule that resulted (whether it was brand new or already known —
   * either way, this explains where the example ended up), {@code reasonForMoveToDone} is set
   * when (and only when) the example was filed to {@code mom-rules/Done} — either because nothing
   * could be learned from it, or because it was learned successfully but its action didn't
   * relocate the message on its own. The two aren't mutually exclusive: a successful learn whose
   * action didn't move the message sets both.
   */
  public static void recordLearningOutcome(File decisionsDir, int retentionDays, String subject, String from, String learnedRule, String reasonForMoveToDone) {
    write(decisionsDir, retentionDays, Trigger.LEARNING.name(), subject, from, null, learnedRule, reasonForMoveToDone);
  }

  private static synchronized void write(File decisionsDir, int retentionDays, String trigger, String subject, String from,
                                          List<RuleOutcome> rules, String learnedRule, String reasonForMoveToDone) {
    // 0 (or absent, same thing for a plain int field read from a config.json predating this
    // feature) means "never asked for this" — these records carry real mail metadata (Subject,
    // From), so logging is opt-in, not on-by-default like StatsLog's anonymous counts.
    if (decisionsDir == null || retentionDays <= 0) return;

    ZonedDateTime nowUtc = Instant.now().atZone(ZoneOffset.UTC);
    String today = FILE_DATE_FORMAT.format(nowUtc);
    File file = new File(decisionsDir, FILE_PREFIX + today + ".json");

    try {
      Files.createDirectories(decisionsDir.toPath());
      // Lazy, like StatsLog's own file-per-day: no scheduler, just notice on the first write of a
      // new day that today's file doesn't exist yet, and catch up whatever rotation is due then.
      if (!file.isFile()) {
        rotate(decisionsDir, today, retentionDays);
      }
      DecisionEntry entry = new DecisionEntry(EVENT_DATE_FORMAT.format(nowUtc) + "Z", trigger, subject, from, rules, learnedRule, reasonForMoveToDone);
      String line = GSON.toJson(entry);
      try (Writer writer = new FileWriter(file, true)) {
        writer.write(line);
        writer.write(System.lineSeparator());
      }
    } catch (IOException e) {
      LOGGER.log(Level.WARNING, "Could not write decision log entry to " + file, e);
    }
  }

  /**
   * Compresses every day file other than today's that isn't already a {@code .lz4} (there can be
   * more than one if the account went quiet for a few days — each gets compressed in place, not
   * just "yesterday"), then deletes anything — compressed or not — older than
   * {@code retentionDays}.
   */
  private static void rotate(File decisionsDir, String today, int retentionDays) {
    File[] files = decisionsDir.listFiles();
    if (files == null) return;
    LocalDate cutoff = LocalDate.now(ZoneOffset.UTC).minusDays(retentionDays);
    for (File f : files) {
      String name = f.getName();
      boolean compressed = name.endsWith(".lz4");
      String plainName = compressed ? name.substring(0, name.length() - ".lz4".length()) : name;
      Matcher m = DAY_FILE.matcher(plainName);
      if (!m.matches()) continue;

      // "Every new day we delete the today-retention file": the file dated exactly
      // retentionDays ago is itself the one to go, not the last one kept — so <=, not <.
      LocalDate fileDate = LocalDate.parse(m.group(1));
      if (!fileDate.isAfter(cutoff)) {
        if (!f.delete()) LOGGER.warning("Could not delete expired decision log file " + f);
      } else if (!compressed && !m.group(1).equals(today)) {
        compress(f);
      }
    }
  }

  /** Compresses source into source.lz4, then deletes source — same temp-file-then-atomic-move approach as {@link LogRotator}. */
  private static void compress(File source) {
    File destination = new File(source.getParentFile(), source.getName() + ".lz4");
    File tmp = new File(destination.getParentFile(), destination.getName() + ".tmp");
    try {
      try (InputStream in = new BufferedInputStream(new FileInputStream(source));
          OutputStream out = new LZ4FrameOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)))) {
        in.transferTo(out);
      }
      Files.move(tmp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      Files.delete(source.toPath());
    } catch (IOException e) {
      LOGGER.log(Level.WARNING, "Could not compress decision log file " + source, e);
    }
  }

  /** Null fields are omitted from the JSON output (default Gson behavior) — never emitted as "field":null. */
  private static final class DecisionEntry {
    private final String date;
    private final String trigger;
    private final String subject;
    private final String from;
    private final List<RuleOutcome> rules;
    private final String learnedRule;
    private final String reasonForMoveToDone;

    private DecisionEntry(String date, String trigger, String subject, String from, List<RuleOutcome> rules,
                           String learnedRule, String reasonForMoveToDone) {
      this.date = date;
      this.trigger = trigger;
      this.subject = subject;
      this.from = from;
      this.rules = rules;
      this.learnedRule = learnedRule;
      this.reasonForMoveToDone = reasonForMoveToDone;
    }
  }
}
