package net.pieroxy.mom.utils.logging;

import com.google.gson.Gson;
import net.pieroxy.mom.api.metadata.TypeScriptNonConstEnum;
import net.pieroxy.mom.api.metadata.TypeScriptType;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

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
  private final static DateTimeFormatter EVENT_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  final static String FILE_PREFIX = "decisions-";
  private final static DailyLogFiles DAILY_FILES = new DailyLogFiles(FILE_PREFIX, ".json");

  /** Which code path produced a decision — see {@code RuleHelper#processRules}/{@code RuleLearner}. */
  @TypeScriptType
  @TypeScriptNonConstEnum
  public enum Trigger { INBOX, LEARNING, MANUAL_REPROCESSING }

  /**
   * One rule's outcome within a single message's {@code INBOX}/{@code MANUAL_REPROCESSING}
   * decision (see {@link #recordRuleEvaluation}) — one entry per rule actually reached, in
   * evaluation order; a rule after the one that blocked simply has no entry, since it never ran.
   * This includes every individual learned rule actually reached inside a {@code LEARNED_RULES}
   * group, each as its own entry (tagged {@code learnedRule=true}), not one opaque entry for the
   * whole group — a single learned rule with a long key list (e.g. 372 addresses) still counts as
   * just one entry, same as a manual rule with a long key list; it's *distinct* learned rules
   * that each get their own line, exactly like distinct manual rules do.
   *
   * @param rule           {@code RuleInterface#describe()} — identifies the rule; no separate
   *                       name field exists yet, so this is always the auto-generated
   *                       {@code Rule(matcher,action)} form today.
   * @param ruleName       the rule's name at the time (see {@code Rule#getName}), null if unnamed.
   * @param learnedRule    true if this entry is one of the account's learned rules rather than a
   *                       manually-configured one — the two otherwise look identical (same
   *                       {@code describe()} format), so this is the only way to tell them apart.
   * @param matched        true/false on a clean evaluation; null if the matcher threw before a
   *                       match could even be determined (see {@code exception}) — a crashed rule
   *                       is deliberately not conflated with one that cleanly didn't match.
   * @param matchDetail    only present when matched is true: the {@code MatchResult} debug string,
   *                       i.e. what actually hit (which key, which list, the score...).
   * @param actionName     only present when matched is true: the action's name at the time (see
   *                       {@code ActionNames#nameOf}).
   * @param spam           only present when matched is true: the action's spam flag at the time.
   * @param keepProcessing only present when matched is true: whether this match let evaluation
   *                       carry on to the next rule, or stopped it here.
   * @param exception      {@code toString()} of whatever was thrown, if anything — set whether or
   *                       not matched is true (a rule can match cleanly and then have its action
   *                       throw; matched stays true, and this explains what went wrong applying
   *                       the consequence).
   */
  public record RuleOutcome(String rule, String ruleName, boolean learnedRule, Boolean matched, String matchDetail, String actionName, Boolean spam,
                            Boolean keepProcessing, String exception) {}

  private DecisionLog() {}

  /**
   * One entry per message evaluated against the rule catalog — INBOX or MANUAL_REPROCESSING (see
   * {@code RuleHelper#processRules}).
   *
   * @param messageId   the {@code Message-ID} header; null (omitted) if the message has none.
   * @param processedMs time spent running the whole rule chain, same as {@code StatsLog}'s.
   */
  public static void recordRuleEvaluation(File decisionsDir, int retentionDays, Trigger trigger, String messageId, String subject, String from,
                                          long processedMs, List<RuleOutcome> rules) {
    write(decisionsDir, retentionDays, trigger.name(), messageId, subject, from, processedMs, rules, null, null);
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
  public static void recordLearningOutcome(File decisionsDir, int retentionDays, String messageId, String subject, String from,
                                           String learnedRule, String reasonForMoveToDone) {
    write(decisionsDir, retentionDays, Trigger.LEARNING.name(), messageId, subject, from, null, null, learnedRule, reasonForMoveToDone);
  }

  private static synchronized void write(File decisionsDir, int retentionDays, String trigger, String messageId, String subject, String from,
                                          Long processedMs, List<RuleOutcome> rules, String learnedRule, String reasonForMoveToDone) {
    // 0 (or absent, same thing for a plain int field read from a config.json predating this
    // feature) means "never asked for this" — these records carry real mail metadata (Subject,
    // From), so logging is opt-in, not on-by-default like StatsLog's anonymous counts.
    if (decisionsDir == null || retentionDays <= 0) return;

    ZonedDateTime nowUtc = Instant.now().atZone(ZoneOffset.UTC);
    Entry entry = new Entry(EVENT_DATE_FORMAT.format(nowUtc) + "Z", trigger, messageId, subject, from, processedMs, rules,
        learnedRule, reasonForMoveToDone);
    try {
      DAILY_FILES.append(decisionsDir, nowUtc.toLocalDate(), GSON.toJson(entry), retentionDays);
    } catch (IOException e) {
      LOGGER.log(Level.WARNING, "Could not write decision log entry to " + decisionsDir, e);
    }
  }

  /**
   * One line of a decisions file, as written here and read back by {@link DecisionReader}. Null
   * fields are omitted from the JSON output (default Gson behavior), never emitted as "field":null.
   */
  public record Entry(String date, String trigger, String messageId, String subject, String from, Long processedMs,
                      List<RuleOutcome> rules, String learnedRule, String reasonForMoveToDone) {}
}
