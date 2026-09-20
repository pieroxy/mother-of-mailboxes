package net.pieroxy.imf.detection.classifier;

import opennlp.tools.doccat.DoccatFactory;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.doccat.FeatureGenerator;
import opennlp.tools.ml.maxent.quasinewton.QNTrainer;
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
 * (Re)trains a header classifier (SPAM/HAM) from the same corpus as {@link SubjectClassifierTrainer},
 * but on structured header-derived facts ({@link HeaderFeatureGenerator}) instead of the subject
 * text — sender/recipient domains, display names, In-Reply-To/List-Id/Precedence,
 * Return-Path/Reply-To alignment with From. Uses {@code MAXENT_QN} rather than the subject
 * classifier's Naive Bayes: several of these features are correlated by construction (e.g.
 * {@code listUnsubscribe=true} and {@code precedence=bulk} tend to show up together), which
 * violates Naive Bayes' independence assumption but is exactly what a jointly-optimized Maxent
 * model handles natively.
 * <p>
 * Separate model file ({@link ClassifierCorpusStore#getHeaderModelFile()}) from the subject
 * classifier, so the two can run and be evaluated side by side (see
 * {@code net.pieroxy.imf.config.general.MailFilterRuleConfiguration#isKeepProcessing()} for wiring a
 * rule that compares them without either one blocking the mail's normal handling).
 */
public class HeaderClassifierTrainer {
  private final static Logger LOGGER = Logger.getLogger(HeaderClassifierTrainer.class.getName());
  static final int MIN_EXAMPLES_PER_CLASS = 50;
  private static final String[] NO_TOKENS = new String[0];

  private final ClassifierCorpusStore corpusStore;

  public HeaderClassifierTrainer(ClassifierCorpusStore corpusStore) {
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
      LOGGER.info("Header classifier training skipped: not enough data yet (" + spamCount + " spam / "
          + hamCount + " ham, need at least " + MIN_EXAMPLES_PER_CLASS + " of each)");
      return;
    }

    List<DocumentSample> samples = examples.stream()
        .filter(e -> e.getLabel() != null)
        .map(e -> new DocumentSample(e.getLabel().name(), NO_TOKENS, Map.of(HeaderFeatureGenerator.EXAMPLE_KEY, e)))
        .collect(Collectors.toList());

    TrainingParameters params = new TrainingParameters();
    params.put(TrainingParameters.ALGORITHM_PARAM, QNTrainer.MAXENT_QN_VALUE);
    // Same reasoning as the subject classifier's cutoff=1: this corpus size is small enough that
    // the default cutoff (5 occurrences minimum) would drop most features before they get a
    // chance to matter.
    params.put(TrainingParameters.CUTOFF_PARAM, 1);

    DoccatFactory factory = new DoccatFactory(new FeatureGenerator[]{new HeaderFeatureGenerator()});
    DoccatModel model = DocumentCategorizerME.train("en", toStream(samples), params, factory);

    File modelFile = corpusStore.getHeaderModelFile();
    modelFile.getParentFile().mkdirs();
    File tmp = new File(modelFile.getParentFile(), modelFile.getName() + ".tmp");
    model.serialize(tmp);
    Files.move(tmp.toPath(), modelFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

    LOGGER.info("Header classifier trained: " + spamCount + " spam / " + hamCount + " ham example(s)");
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
