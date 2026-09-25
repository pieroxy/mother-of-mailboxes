package net.pieroxy.mom.rules;

import net.pieroxy.mom.detection.classifier.BodyClassifierTrainer;
import net.pieroxy.mom.detection.classifier.ClassifierCorpusScanner;
import net.pieroxy.mom.detection.classifier.ClassifierCorpusStore;
import net.pieroxy.mom.detection.classifier.ClassifierScanState;
import net.pieroxy.mom.detection.classifier.ClassifierScanStateStore;
import net.pieroxy.mom.detection.classifier.HeaderClassifierTrainer;
import net.pieroxy.mom.detection.classifier.SubjectClassifierTrainer;
import net.pieroxy.mom.config.credentials.Credential;
import net.pieroxy.mom.config.general.MailAccountConfiguration;
import net.pieroxy.mom.learning.LearnedRulesStore;
import net.pieroxy.mom.learning.RuleLearner;
import net.pieroxy.mom.utils.mail.ImapIdleWatcher;
import net.pieroxy.mom.utils.mail.ImapMailbox;
import net.pieroxy.mom.utils.mail.ImapMailboxConnection;
import net.pieroxy.mom.utils.mail.ImapMailboxFactory;
import net.pieroxy.mom.utils.scheduling.BackoffLoop;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Orchestrates processing for an account: schedules cycles (via {@link BackoffLoop}), fetches
 * new messages (via {@link ImapMailbox}) and tracks progress (via {@link MailAccountStateStore}).
 * Knows no detail of IMAP connections or persistence: each responsibility lives in its own
 * class, injectable/testable on its own.
 */
public class MailAccount implements Runnable {
  private final static Logger LOGGER = Logger.getLogger(MailAccount.class.getName());
  private final static long MAX_BACKOFF_MS = 30 * 60 * 1000L; // 30 minutes
  // A month is comfortably longer than anyone takes to notice and rescue a false positive from Spam.
  private final static int PROCESSED_FINGERPRINT_RETENTION_DAYS = 30;

  private final MailAccountConfiguration config;
  private final Credential credential;
  private final MailAccountStateStore stateStore;
  private final LearnedRulesStore learnedRulesStore;
  private final RuleCatalog ruleCatalog;
  private final int classifierCorpusRetentionDays;
  private final int classifierCorpusScanBatchSize;
  private final ClassifierScanStateStore classifierScanStateStore;
  private final ClassifierCorpusStore classifierCorpusStore;
  private final SubjectClassifierTrainer subjectClassifierTrainer;
  private final HeaderClassifierTrainer headerClassifierTrainer;
  private final BodyClassifierTrainer bodyClassifierTrainer;
  private final String classifierSpamFolderName;
  private final List<String> classifierExcludedFolders;
  private final ImapMailboxFactory mailboxFactory;
  private final ImapIdleWatcher idleWatcher;
  private final Thread thread;
  private LocalDate lastSkeletonEnsureDate;

  // Live dashboard state (see AccountsApi): written only from this account's own thread, read
  // from Tomcat's request-handling threads — volatile/Atomic, not synchronized, is enough since
  // there's a single writer.
  private volatile AccountStatus status = AccountStatus.OK;
  private final AtomicLong messagesProcessed = new AtomicLong();
  private final AtomicLong messagesMatched = new AtomicLong();
  // Kept around after a subsequent successful cycle (not cleared back to null): a dashboard
  // showing OK is more useful if it can still say when/why the account last had trouble.
  private volatile String lastErrorMessage;
  private volatile Instant lastErrorTimestamp;

  public MailAccount(MailAccountConfiguration config, Credential credential, String dataFolder) {
    this(config, credential, dataFolder, ImapMailboxConnection::connect);
  }

  /** Visible for tests: lets a mailbox factory be injected without real IMAPS/TLS. */
  MailAccount(MailAccountConfiguration config, Credential credential, String dataFolder, ImapMailboxFactory mailboxFactory) {
    this.config = config;
    this.credential = credential;
    this.stateStore = new MailAccountStateStore(dataFolder, config.getDisplayName());
    this.learnedRulesStore = new LearnedRulesStore(dataFolder, config.getDisplayName());
    this.classifierCorpusRetentionDays = config.getClassifierCorpusRetentionDays();
    this.classifierCorpusScanBatchSize = config.getClassifierCorpusScanBatchSize();
    this.classifierScanStateStore = new ClassifierScanStateStore(dataFolder, config.getDisplayName());
    this.classifierCorpusStore = new ClassifierCorpusStore(dataFolder, config.getDisplayName(), classifierCorpusRetentionDays);
    // Built before ruleCatalog and handed to it: SubjectClassifierMatcher/HeaderClassifierMatcher/
    // BodyClassifierMatcher have no other way to know which account's model file to load, since
    // they're built without context by MatcherType.getImplementation() — see RuleContext.
    RuleContext ruleContext = new RuleContext(classifierCorpusStore.getModelFile(), classifierCorpusStore.getHeaderModelFile(),
        classifierCorpusStore.getBodyModelFile(), new File(dataFolder, "logs"));
    this.ruleCatalog = new RuleCatalog(config.getRules(), learnedRulesStore, ruleContext);
    this.subjectClassifierTrainer = new SubjectClassifierTrainer(classifierCorpusStore);
    this.headerClassifierTrainer = new HeaderClassifierTrainer(classifierCorpusStore);
    this.bodyClassifierTrainer = new BodyClassifierTrainer(classifierCorpusStore);
    String spamFolderName = config.getClassifierSpamFolderName();
    this.classifierSpamFolderName = (spamFolderName == null || spamFolderName.isBlank()) ? "Spam" : spamFolderName;
    this.classifierExcludedFolders = config.getClassifierExcludedFolders() != null
        ? config.getClassifierExcludedFolders() : List.of();
    this.mailboxFactory = mailboxFactory;
    this.idleWatcher = new ImapIdleWatcher(config, credential);
    // Not started here: Thread's constructor only stores the Runnable, and this is only ever
    // read (start()/requestStop()/join()) after construction completes — see Runner#main.
    this.thread = new Thread(this, "mail-account-" + config.getDisplayName());
  }

  /** Starts this account's processing thread. */
  public void start() {
    thread.start();
  }

  /**
   * For a responsive shutdown (see {@code Runner#shutdown}): interrupts this account's thread —
   * which ends the wait between cycles if it's a plain sleep, and prevents a new cycle from
   * starting once the current one (if any) finishes — and, since an IMAP IDLE wait is a blocked
   * socket read that {@code Thread.interrupt()} alone can't reach, also forces that wait to end
   * right away instead of leaving it to time out on its own (up to {@link ImapIdleWatcher}'s
   * slice length). A no-op on the IDLE side if the thread is doing anything else right now (an
   * active {@code processMessages()} cycle, say) — the interrupt alone is what handles that case,
   * same as for a plain sleep.
   */
  public void requestStop() {
    thread.interrupt();
    idleWatcher.interruptNow();
  }

  /** Waits up to timeoutMs for this account's thread to finish — see {@link Thread#join(long)}. */
  public void join(long timeoutMs) throws InterruptedException {
    thread.join(timeoutMs);
  }

  /** displayName if set, otherwise the IMAP login — surfaced by the web API's account list. */
  public String getAccountLabel() {
    return accountLabel();
  }

  public AccountStatus getStatus() {
    return status;
  }

  /** Messages processed this session (reset on restart) — surfaced by the web API's account list. */
  public long getMessagesProcessed() {
    return messagesProcessed.get();
  }

  /** Of those, how many matched a rule whose action was not NOOP. */
  public long getMessagesMatched() {
    return messagesMatched.get();
  }

  /** Message of the account's most recent cycle failure this session, or null if none happened. */
  public String getLastErrorMessage() {
    return lastErrorMessage;
  }

  /** When {@link #getLastErrorMessage()} was recorded, or null if none happened this session. */
  public Instant getLastErrorTimestamp() {
    return lastErrorTimestamp;
  }

  @Override
  public void run() {
    LOGGER.info("Starting account " + config.getDisplayName());
    // Builds the Matcher/Action tree right away rather than waiting for the first message:
    // RuleCatalog is normally lazy (see inspect()), but some matchers (like
    // SubjectClassifierMatcher) need to be built to announce their state right at startup —
    // otherwise, on an account that doesn't receive anything right away, we'd never know
    // whether the classifier is active or not.
    ruleCatalog.get();
    ruleCatalog.logRules(LOGGER, accountLabel());

    // Everything in processMessages() but the INBOX scan can tolerate the full runEvery delay
    // (see MailAccountConfiguration.runEvery); only new mail sitting unclassified in the INBOX
    // is time-sensitive. Rather than shrinking runEvery for everything, the wait between cycles
    // watches the INBOX via IMAP IDLE (see ImapIdleWatcher) and returns early the instant new
    // mail arrives — runEvery then only bounds the worst case (a server without IDLE support, or
    // a watch that failed for this cycle). The watcher always closes its connection before a
    // cycle's own connection opens INBOX: some IMAP servers refuse a second, concurrent SELECT
    // of the same mailbox, so the two must never overlap.
    new BackoffLoop(config.getRunEvery() * 1000L, MAX_BACKOFF_MS, idleWatcher)
        .run(config.getDisplayName(), this::processMessagesTracked);
  }

  /**
   * Wraps {@link #processMessages} to keep {@link #status} current for the dashboard: PROCESSING
   * while a cycle runs, OK right after one finishes cleanly (i.e. back to idling/IDLE-waiting),
   * KO if it threw. Kept separate from {@link #processMessages} itself rather than folded into
   * {@link BackoffLoop}, which deliberately stays unaware of what the task it runs represents.
   */
  private void processMessagesTracked() throws Exception {
    status = AccountStatus.PROCESSING;
    try {
      processMessages();
      status = AccountStatus.OK;
    } catch (Exception e) {
      status = AccountStatus.KO;
      lastErrorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
      lastErrorTimestamp = Instant.now();
      throw e;
    }
  }

  /** displayName if set, otherwise falls back to the IMAP login — see {@link RuleCatalog#logRules}. */
  private String accountLabel() {
    String displayName = config.getDisplayName();
    return (displayName == null || displayName.isBlank()) ? credential.getUsername() : displayName;
  }

  /** Applies the first matching rule (manual config, then learned rules). */
  private void inspect(Message message) {
    RuleHelper.ProcessOutcome outcome = RuleHelper.processRules(ruleCatalog.get(), ruleCatalog.getLearnedRulesFallback(), message, LOGGER,
        "account " + config.getDisplayName(), ruleCatalog.getContext());
    messagesProcessed.incrementAndGet();
    if (outcome.nonNoopActionApplied()) messagesMatched.incrementAndGet();
  }

  /** Package-private (instead of private): lets MailAccountTest run a cycle without going through run()/BackoffLoop. */
  void processMessages() throws MessagingException {
    LOGGER.info("Processing account " + config.getDisplayName());
    try (ImapMailbox mailbox = mailboxFactory.connect(config, credential)) {
      RuleLearner learner = new RuleLearner(mailbox, learnedRulesStore, config.getLearningShortcuts(), config.isDiscoveryTreeDisabled());
      ManualReprocessor reprocessor = new ManualReprocessor(mailbox, ruleCatalog);
      ensureFolderSkeletonsIfDue(learner, reprocessor);

      if (learner.learnFromExamples()) {
        ruleCatalog.invalidate();
        ruleCatalog.get(); // rebuilds right away (see the comment in run())
      }

      processNewMessages(mailbox);

      reprocessor.reprocessPending();

      if (classifierCorpusRetentionDays > 0) {
        scanSpamFolderForClassifierCorpus(mailbox);
        scanClassifierCorpusIfDue(mailbox);
      }
    }
  }

  /**
   * Unlike the rest of the tree (once a day, see below), Spam is scanned every cycle: it's the
   * one folder a user is likely to empty out themselves before the next daily scan (e.g. a
   * manual purge every evening) — if we waited until the next day, all of yesterday's spam
   * would be gone before ever being captured for the corpus. Shares the same (per-folder) state
   * as the daily scan, so there's no double counting between the two.
   */
  private void scanSpamFolderForClassifierCorpus(ImapMailbox mailbox) {
    ClassifierScanState state = classifierScanStateStore.load();
    try {
      new ClassifierCorpusScanner(mailbox, classifierCorpusStore, classifierSpamFolderName, classifierExcludedFolders,
          accountLabel(), classifierCorpusScanBatchSize, classifierCorpusRetentionDays).scanSpamFolderNow(state);
      classifierScanStateStore.save(state);
    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Classifier corpus [" + accountLabel() + "] spam scan failed", e);
    }
  }

  /**
   * The "mom-rules/..." folders almost never change once created: no need to recheck their
   * existence every cycle (potentially every minute depending on runEvery). Once at startup
   * (lastSkeletonEnsureDate is still null) then once per calendar day afterward is enough to
   * recover from an accidental deletion without waiting for a restart.
   */
  private void ensureFolderSkeletonsIfDue(RuleLearner learner, ManualReprocessor reprocessor) throws MessagingException {
    LocalDate today = LocalDate.now();
    if (today.equals(lastSkeletonEnsureDate)) return;
    learner.ensureFolderSkeleton();
    reprocessor.ensureFolderSkeleton();
    lastSkeletonEnsureDate = today;
  }

  /**
   * Scans at most once per calendar day once caught up (no dedicated scheduler: it piggybacks
   * on the cycle already in progress, over the same IMAP connection). As long as there's
   * backlog left to catch up on (scan() is capped, see {@link ClassifierCorpusScanner}), it
   * relaunches on the next cycle instead of waiting for the next day, to catch up on history
   * over several quick cycles rather than one endless one. An error here never blocks normal
   * message processing, which just finished successfully right above.
   */
  private void scanClassifierCorpusIfDue(ImapMailbox mailbox) {
    LocalDate today = LocalDate.now();
    ClassifierScanState state = classifierScanStateStore.load();
    if (today.toString().equals(state.getLastScanDate())) return;

    boolean caughtUpToday;
    try {
      boolean moreWorkPending = new ClassifierCorpusScanner(mailbox, classifierCorpusStore, classifierSpamFolderName,
          classifierExcludedFolders, accountLabel(), classifierCorpusScanBatchSize, classifierCorpusRetentionDays).scan(state, today);
      caughtUpToday = !moreWorkPending;
      if (caughtUpToday) {
        state.setLastScanDate(today.toString());
      }
      classifierScanStateStore.save(state);
    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Classifier corpus [" + accountLabel() + "] scan failed", e);
      return;
    }

    // Separate from the try above: a training failure must not prevent the (already successful)
    // scan from having marked the day as done, otherwise the scan would be relaunched every
    // cycle for nothing even though it worked fine.
    if (caughtUpToday) {
      try {
        subjectClassifierTrainer.train();
      } catch (Exception e) {
        LOGGER.log(Level.WARNING, "Subject classifier training failed for account " + config.getDisplayName(), e);
      }
      // Separate try/catch: a failure training one classifier must not skip the others.
      try {
        headerClassifierTrainer.train();
      } catch (Exception e) {
        LOGGER.log(Level.WARNING, "Header classifier training failed for account " + config.getDisplayName(), e);
      }
      try {
        bodyClassifierTrainer.train();
      } catch (Exception e) {
        LOGGER.log(Level.WARNING, "Body classifier training failed for account " + config.getDisplayName(), e);
      }
    }
  }

  /**
   * Only processes messages whose UID is strictly greater than the last known UID, so a message
   * is never inspected twice from one cycle to the next — except that a message manually moved
   * back into a scanned folder (e.g. a false positive rescued from Spam back to INBOX) shows up
   * there under a brand new UID, above the cursor, indistinguishable from genuinely new mail. To
   * catch that case too, every inspected message is fingerprinted (see {@link #fingerprintOf}) and
   * remembered regardless of UID/folder: seeing the same fingerprint again just advances the
   * cursor past it, without re-running the rules (and re-undoing the user's manual rescue).
   */
  private void processNewMessages(ImapMailbox mailbox) throws MessagingException {
    MailAccountState state = stateStore.load();

    long uidValidity = mailbox.getUidValidity();
    if (state.getUidValidity() != uidValidity) {
      // First run for this account, or UIDVALIDITY changed server-side (mailbox recreated): the
      // old UIDs no longer mean anything. Start over from "now" rather than replaying the whole
      // mailbox history.
      state.setUidValidity(uidValidity);
      state.setLastUid(mailbox.getUidNext() - 1);
    }

    LocalDate today = LocalDate.now();
    for (Message message : mailbox.getMessagesSince(state.getLastUid())) {
      long uid = mailbox.getUid(message);
      try {
        String fingerprint = fingerprintOf(message);
        if (state.isProcessed(fingerprint)) {
          LOGGER.fine("Skipping message UID " + uid + " on account " + config.getDisplayName()
              + ": already processed earlier (likely manually moved back into a scanned folder)");
        } else {
          inspect(message);
          state.markProcessed(fingerprint, today);
        }
      } catch (Exception e) {
        LOGGER.log(Level.WARNING, "Failed to inspect message UID " + uid + " on account " + config.getDisplayName(), e);
      }
      state.setLastUid(uid);
    }
    state.pruneProcessedFingerprintsBefore(today.minusDays(PROCESSED_FINGERPRINT_RETENTION_DAYS));

    stateStore.save(state);
  }

  /**
   * Identifies a message across folders/UIDs, without trusting anything the sender controls
   * (a spammer-supplied Message-ID is sometimes missing, sometimes reused verbatim across an
   * entire campaign — useless as a dedup key for exactly the mail this matters most for).
   * {@link Message#getReceivedDate()} is the IMAP server's own INTERNALDATE — assigned once, at
   * delivery, by the server itself — combined with From/To/Subject. RFC 3501 §6.4.7 has servers
   * preserve both flags and internal date across COPY/MOVE, so this stays stable when a message
   * is relocated, including by the user dragging it from one folder to another by hand.
   */
  private static String fingerprintOf(Message message) throws MessagingException {
    String received = message.getReceivedDate() != null ? message.getReceivedDate().toInstant().toString() : "";
    String from = addressesOf(message.getFrom());
    String to = addressesOf(message.getRecipients(Message.RecipientType.TO));
    String subject = message.getSubject() != null ? message.getSubject() : "";
    String raw = String.join(" ", received, from, to, subject);
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is a mandatory JDK algorithm", e);
    }
  }

  private static String addressesOf(Address[] addresses) {
    if (addresses == null) return "";
    return Arrays.stream(addresses).map(Address::toString).collect(Collectors.joining(","));
  }
}
