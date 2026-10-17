package net.pieroxy.mom.rules.matchers.implementations;

import net.pieroxy.mom.detection.classifier.BodyClassifierTrainer;
import net.pieroxy.mom.detection.classifier.BodyFeatureGenerator;
import net.pieroxy.mom.detection.classifier.ClassifierCorpusScanner;
import net.pieroxy.mom.detection.classifier.ClassifierExample;
import net.pieroxy.mom.detection.classifier.ClassifierExampleExtractor;
import net.pieroxy.mom.detection.classifier.ClassifierLabel;
import net.pieroxy.mom.config.general.MailFilterRuleMatcherConfiguration;
import net.pieroxy.mom.rules.RuleContext;
import net.pieroxy.mom.rules.matchers.MatchResult;
import net.pieroxy.mom.rules.matchers.Matcher;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.util.ext.ExtensionLoader;

import javax.mail.Message;
import javax.mail.MessagingException;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.logging.Level;

/**
 * Classifies the message body's visible text via the model trained by
 * {@link BodyClassifierTrainer} on the corpus collected by
 * {@link ClassifierCorpusScanner}. Same contract as
 * {@link SubjectClassifierMatcher}/{@link HeaderClassifierMatcher} otherwise: not learnable by
 * example, the config key is a probability threshold, and it's meant to run alongside the other
 * two rather than replace them.
 * <p>
 * Unlike {@link SubjectClassifierMatcher}, there's no early "nothing to classify" exit for an
 * empty body: {@link BodyFeatureGenerator} treats where the body text came from — HTML, plain
 * text, or neither (see {@code bodySource}) — as a feature the model was trained on, not a
 * reason to skip classification.
 */
public class BodyClassifierMatcher extends Matcher {
  private static final String[] NO_TOKENS = new String[0];

  static {
    // Same reasoning as HeaderClassifierMatcher: a model loaded back from disk instantiates its
    // FeatureGenerator(s) by class name via reflection, refused unless the package is trusted.
    ExtensionLoader.registerAllowedPackage(BodyFeatureGenerator.class.getPackageName());
  }

  private ReputationThreshold threshold;
  private File modelFile;

  private DocumentCategorizerME categorizer;
  private long loadedModelMtime = -1;
  private boolean loggedInactive;

  @Override
  protected void bindContext(RuleContext context) {
    modelFile = context.bodyModelFile();
  }

  @Override
  public void setConfig(MailFilterRuleMatcherConfiguration config) {
    super.setConfig(config);
    threshold = ReputationThreshold.parse(config.getKey(), "BODY_CLASSIFIER_EQUALS");
    // Same reasoning as the other two classifier matchers: check/log the state right away rather
    // than waiting for the first message.
    loadCategorizer();
  }

  @Override
  public MatchResult matches(Message message) throws MessagingException {
    DocumentCategorizerME model = loadCategorizer();
    if (model == null) {
      return notMatched();
    }

    ClassifierExample example = ClassifierExampleExtractor.extract(message, null, Instant.now());
    String bodyText = example.getBodyText();
    String[] tokens = bodyText == null || bodyText.isBlank() ? NO_TOKENS : SimpleTokenizer.INSTANCE.tokenize(bodyText);
    double[] outcome = model.categorize(tokens, Map.of(BodyFeatureGenerator.EXAMPLE_KEY, example));
    double spamScore = outcome[model.getIndex(ClassifierLabel.SPAM.name())];
    boolean matched = threshold.test(spamScore);
    getLogger().fine(() -> "body spam score=" + spamScore + " against " + threshold
        + " -> " + (matched ? "match" : "no match"));
    return matched ? matched("score=" + spamScore) : notMatched();
  }

  /** Loads/reloads the model if needed — same self-detecting-mtime mechanism as {@link SubjectClassifierMatcher#loadCategorizer()}. */
  private DocumentCategorizerME loadCategorizer() {
    if (modelFile == null) {
      logInactiveOnce("no classifier model context for this account");
      return null;
    }

    long currentMtime = modelFile.lastModified(); // 0 if the file doesn't exist
    if (currentMtime == 0) {
      categorizer = null;
      loadedModelMtime = -1;
      logInactiveOnce("no trained model yet for this account (not enough data collected so far)");
      return null;
    }

    if (categorizer == null || currentMtime != loadedModelMtime) {
      try {
        categorizer = new DocumentCategorizerME(new DoccatModel(modelFile));
        loadedModelMtime = currentMtime;
        loggedInactive = false; // active again: a future disappearance will be re-logged
        getLogger().info("Loaded classifier model " + modelFile + " (trained " + Instant.ofEpochMilli(currentMtime) + ")");
      } catch (IOException | RuntimeException e) {
        // RuntimeException included: an incompatible model (e.g. a FeatureGenerator that moved
        // package since training — ExtensionNotLoadedException) must degrade this matcher, not
        // take down the whole account thread.
        getLogger().log(Level.WARNING, "Failed to load classifier model " + modelFile, e);
        categorizer = null;
      }
    }
    return categorizer;
  }

  private void logInactiveOnce(String reason) {
    if (!loggedInactive) {
      getLogger().info("BodyClassifierMatcher inactive: " + reason);
      loggedInactive = true;
    }
  }
}
