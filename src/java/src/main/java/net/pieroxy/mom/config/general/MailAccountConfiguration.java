package net.pieroxy.mom.config.general;

import net.pieroxy.mom.utils.CredentialsResolver;
import net.pieroxy.mom.detection.classifier.ClassifierCorpusScanner;

import java.util.List;

public class MailAccountConfiguration {
  private String host;
  private int port;
  /** Key into credentials.json's top-level "credentials" map — see {@link CredentialsResolver}. */
  private String credentials;
  private String displayName;
  /**
   * Time to sleep between two runs, in seconds.
   */
  private int runEvery;
  /**
   * Name of the IMAP folder considered spam for the classifier corpus (varies by provider:
   * "Spam" for most, "[Gmail]/Spam" for Gmail, "Junk Email" for Outlook...). Default: "Spam"
   * if absent/blank.
   */
  private String classifierSpamFolderName;
  /**
   * Folder names (anywhere in the tree, in addition to INBOX and mom-rules/ which are always
   * excluded already) to exclude entirely from the classifier corpus: neither SPAM nor HAM,
   * ignored. Useful for example for a folder dedicated to the classifier's own verdicts (e.g.
   * "SpamML" or "Spam/ML"), so it doesn't feed itself training examples.
   */
  private List<String> classifierExcludedFolders;
  /** Number of days of classifier corpus files to keep for this account (0 or absent = disabled). */
  private int classifierCorpusRetentionDays;
  /**
   * Cap on messages fetched/processed in one classifier corpus scan cycle for this account (0 or
   * absent = {@link ClassifierCorpusScanner}'s built-in default of
   * 500). Bounds how much of the account's IMAP connection a single cycle can monopolize when
   * catching up on a large backlog (e.g. the first scan of a folder with years of history) —
   * lower it on a slow link or server, raise it to catch up faster on a fast one.
   */
  private int classifierCorpusScanBatchSize;

  private List<MailFilterRuleConfiguration> rules;

  /**
   * Named shortcuts to the discovery folder tree (see
   * {@link net.pieroxy.mom.learning.RuleLearner}): one flat {@code mom-rules/<name>} folder per
   * entry, bound to a fixed (matcher type, action) pair, so an IMAP client only needs to
   * subscribe to the handful actually in use instead of the whole
   * {@code <MATCHER_TYPE>/<ACTION_TYPE>} combinatorial tree.
   */
  private List<LearningShortcutConfiguration> learningShortcuts;

  /**
   * Skips creating/maintaining the {@code <MATCHER_TYPE>/<ACTION_TYPE>} discovery tree under
   * {@code mom-rules/} entirely (default: false, tree created as usual) — for a mail client that
   * shows every IMAP folder unconditionally regardless of subscription state (e.g. Apple Mail),
   * where the tree is pure clutter once {@link #learningShortcuts} covers what's actually used.
   * {@code mom-rules/Done} and any configured shortcut folders are unaffected.
   */
  private boolean discoveryTreeDisabled;

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public String getCredentials() {
    return credentials;
  }

  public void setCredentials(String credentials) {
    this.credentials = credentials;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public int getRunEvery() {
    return runEvery;
  }

  public void setRunEvery(int runEvery) {
    this.runEvery = runEvery;
  }

  public String getClassifierSpamFolderName() {
    return classifierSpamFolderName;
  }

  public void setClassifierSpamFolderName(String classifierSpamFolderName) {
    this.classifierSpamFolderName = classifierSpamFolderName;
  }

  public List<String> getClassifierExcludedFolders() {
    return classifierExcludedFolders;
  }

  public void setClassifierExcludedFolders(List<String> classifierExcludedFolders) {
    this.classifierExcludedFolders = classifierExcludedFolders;
  }

  public int getClassifierCorpusRetentionDays() {
    return classifierCorpusRetentionDays;
  }

  public void setClassifierCorpusRetentionDays(int classifierCorpusRetentionDays) {
    this.classifierCorpusRetentionDays = classifierCorpusRetentionDays;
  }

  public int getClassifierCorpusScanBatchSize() {
    return classifierCorpusScanBatchSize;
  }

  public void setClassifierCorpusScanBatchSize(int classifierCorpusScanBatchSize) {
    this.classifierCorpusScanBatchSize = classifierCorpusScanBatchSize;
  }

  public List<MailFilterRuleConfiguration> getRules() {
    return rules;
  }

  public void setRules(List<MailFilterRuleConfiguration> rules) {
    this.rules = rules;
  }

  public List<LearningShortcutConfiguration> getLearningShortcuts() {
    return learningShortcuts;
  }

  public void setLearningShortcuts(List<LearningShortcutConfiguration> learningShortcuts) {
    this.learningShortcuts = learningShortcuts;
  }

  public boolean isDiscoveryTreeDisabled() {
    return discoveryTreeDisabled;
  }

  public void setDiscoveryTreeDisabled(boolean discoveryTreeDisabled) {
    this.discoveryTreeDisabled = discoveryTreeDisabled;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }
}
