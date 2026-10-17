package net.pieroxy.mom.detection.classifier;

/**
 * Cached spam/ham example counts across an account's retained classifier corpus (see
 * {@link ClassifierCorpusStore#readAll()}) — {@link SubjectClassifierTrainer},
 * {@link HeaderClassifierTrainer} and {@link BodyClassifierTrainer} all train on the exact same
 * corpus, just checked against a different {@code MIN_EXAMPLES_PER_CLASS} threshold each, so
 * they share one cached count instead of each persisting their own copy of the same numbers.
 * Refreshed by each trainer's {@code train()} on every run (see
 * {@link ClassifierCorpusStore#saveTrainingCounts}), whether or not training actually happens
 * that run, so it stays current even while the corpus is still below every threshold. Lets
 * {@code AccountsApi} show training progress without ever decompressing/parsing the whole corpus
 * itself, which {@link ClassifierCorpusStore#readAll()} would require.
 */
public class ClassifierTrainingCounts {
  private long spamCount;
  private long hamCount;

  public ClassifierTrainingCounts() {
  }

  public ClassifierTrainingCounts(long spamCount, long hamCount) {
    this.spamCount = spamCount;
    this.hamCount = hamCount;
  }

  public long getSpamCount() {
    return spamCount;
  }

  public void setSpamCount(long spamCount) {
    this.spamCount = spamCount;
  }

  public long getHamCount() {
    return hamCount;
  }

  public void setHamCount(long hamCount) {
    this.hamCount = hamCount;
  }
}
