package net.pieroxy.mom.detection.classifier;

import opennlp.tools.doccat.BagOfWordsFeatureGenerator;
import opennlp.tools.doccat.DoccatFactory;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.doccat.FeatureGenerator;
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
 * (Re)trains a body classifier (SPAM/HAM) from the same corpus as {@link SubjectClassifierTrainer}
 * and {@link HeaderClassifierTrainer}, but on the message body's visible text (see
 * {@link ClassifierExample#getBodyText()} — HTML stripped down to text via Jsoup, see
 * {@link ClassifierExampleExtractor}) instead of the subject or headers. Bag-of-words like the
 * subject classifier (free text, not structured facts), with one extra feature layered on top
 * ({@link BodyFeatureGenerator}, whether the body came from HTML, plain text, or neither) that a
 * plain bag-of-words generator can't express on its own — stripping erases exactly that
 * distinction.
 * <p>
 * Separate model file ({@link ClassifierCorpusStore#getBodyModelFile()}) from the other two
 * classifiers, so all three can run and be evaluated side by side — same reasoning as
 * {@link HeaderClassifierTrainer}.
 * <p>
 * Both {@link #MIN_EXAMPLES_PER_CLASS} and the cutoff below are more conservative than
 * {@link SubjectClassifierTrainer}'s: body text has a much larger vocabulary than a subject
 * line, so the same corpus size that's safe for subjects leaves this model estimating far more
 * parameters from far less repetition per word — a recipe for memorizing this training set's
 * quirks (a name, a URL, a one-off phrase) rather than learning anything that generalizes.
 */
public class BodyClassifierTrainer {
  private final static Logger LOGGER = Logger.getLogger(BodyClassifierTrainer.class.getName());
  static final int MIN_EXAMPLES_PER_CLASS = 150;
  private static final String[] NO_TOKENS = new String[0];

  private final ClassifierCorpusStore corpusStore;

  public BodyClassifierTrainer(ClassifierCorpusStore corpusStore) {
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
      LOGGER.info("Body classifier training skipped: not enough data yet (" + spamCount + " spam / "
          + hamCount + " ham, need at least " + MIN_EXAMPLES_PER_CLASS + " of each)");
      return;
    }

    // Every labeled example is used, even with no body text at all: bodySource=(absent) is
    // itself a feature (see BodyFeatureGenerator), not a reason to skip the example.
    List<DocumentSample> samples = examples.stream()
        .filter(e -> e.getLabel() != null)
        .map(e -> new DocumentSample(e.getLabel().name(), tokensOf(e), Map.of(BodyFeatureGenerator.EXAMPLE_KEY, e)))
        .collect(Collectors.toList());

    TrainingParameters params = new TrainingParameters();
    params.put(TrainingParameters.ALGORITHM_PARAM, NaiveBayesTrainer.NAIVE_BAYES_VALUE);
    // Unlike the subject classifier's cutoff=1: body text's vocabulary is large enough that a
    // word appearing only once or twice in the whole corpus (a name, a URL, a one-off phrase) is
    // noise, not signal — a stricter cutoff drops those before they can be memorized, without
    // depending on corpus size the way MIN_EXAMPLES_PER_CLASS alone would.
    params.put(TrainingParameters.CUTOFF_PARAM, 3);

    DoccatFactory factory = new DoccatFactory(new FeatureGenerator[]{new BagOfWordsFeatureGenerator(), new BodyFeatureGenerator()});
    DoccatModel model = DocumentCategorizerME.train("en", toStream(samples), params, factory);

    File modelFile = corpusStore.getBodyModelFile();
    modelFile.getParentFile().mkdirs();
    File tmp = new File(modelFile.getParentFile(), modelFile.getName() + ".tmp");
    model.serialize(tmp);
    Files.move(tmp.toPath(), modelFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

    LOGGER.info("Body classifier trained: " + spamCount + " spam / " + hamCount + " ham example(s)");
  }

  private static String[] tokensOf(ClassifierExample example) {
    String bodyText = example.getBodyText();
    return bodyText == null || bodyText.isBlank() ? NO_TOKENS : SimpleTokenizer.INSTANCE.tokenize(bodyText);
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
