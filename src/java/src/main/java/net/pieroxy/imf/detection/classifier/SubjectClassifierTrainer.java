package net.pieroxy.imf.detection.classifier;

import opennlp.tools.doccat.DoccatFactory;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.ml.naivebayes.NaiveBayesTrainer;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * (Re)trains a subject classifier (SPAM/HAM) from the corpus already collected by
 * {@link ClassifierCorpusScanner}. Called once a day, right after a successful, up-to-date
 * corpus scan (same account, no separate scheduler). Only writes a model if the corpus holds
 * enough examples of both classes; below that, does nothing and just logs —
 * {@link net.pieroxy.imf.rules.matchers.implementations.SubjectClassifierMatcher} logs, on its
 * own side, once, that it stays inactive as long as no model exists.
 */
public class SubjectClassifierTrainer {
  private final static Logger LOGGER = Logger.getLogger(SubjectClassifierTrainer.class.getName());
  static final int MIN_EXAMPLES_PER_CLASS = 50;

  private final ClassifierCorpusStore corpusStore;

  public SubjectClassifierTrainer(ClassifierCorpusStore corpusStore) {
    this.corpusStore = corpusStore;
  }

  public void train() throws IOException {
    List<ClassifierExample> examples = corpusStore.readAll();
    Map<ClassifierLabel, Long> counts = examples.stream()
        .filter(e -> e.getLabel() != null)
        .collect(Collectors.groupingBy(ClassifierExample::getLabel, Collectors.counting()));
    long spamCount = counts.getOrDefault(ClassifierLabel.SPAM, 0L);
    long hamCount = counts.getOrDefault(ClassifierLabel.HAM, 0L);

    if (spamCount < MIN_EXAMPLES_PER_CLASS || hamCount < MIN_EXAMPLES_PER_CLASS) {
      LOGGER.info("Subject classifier training skipped: not enough data yet (" + spamCount + " spam / "
          + hamCount + " ham, need at least " + MIN_EXAMPLES_PER_CLASS + " of each)");
      return;
    }

    List<DocumentSample> samples = examples.stream()
        .filter(e -> e.getLabel() != null && e.getSubject() != null && !e.getSubject().isBlank())
        .map(e -> new DocumentSample(e.getLabel().name(), SimpleTokenizer.INSTANCE.tokenize(e.getSubject())))
        .collect(Collectors.toList());

    TrainingParameters params = new TrainingParameters();
    params.put(TrainingParameters.ALGORITHM_PARAM, NaiveBayesTrainer.NAIVE_BAYES_VALUE);
    // Subjects are short: the default cutoff (5 occurrences minimum) would eliminate most of
    // the useful words on a corpus this size.
    params.put(TrainingParameters.CUTOFF_PARAM, 1);

    DoccatModel model = DocumentCategorizerME.train("en", toStream(samples), params, new DoccatFactory());

    File modelFile = corpusStore.getModelFile();
    modelFile.getParentFile().mkdirs();
    File tmp = new File(modelFile.getParentFile(), modelFile.getName() + ".tmp");
    model.serialize(tmp);
    Files.move(tmp.toPath(), modelFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

    LOGGER.info("Subject classifier trained: " + spamCount + " spam / " + hamCount + " ham example(s)");
  }

  private static ObjectStream<DocumentSample> toStream(List<DocumentSample> samples) {
    Iterator<DocumentSample> it = samples.iterator();
    return new ObjectStream<>() {
      @Override public DocumentSample read() {
        return it.hasNext() ? it.next() : null;
      }
    };
  }
}
