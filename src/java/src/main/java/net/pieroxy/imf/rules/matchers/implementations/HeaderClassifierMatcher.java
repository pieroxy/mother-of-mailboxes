package net.pieroxy.imf.rules.matchers.implementations;

import net.pieroxy.imf.detection.classifier.ClassifierExample;
import net.pieroxy.imf.detection.classifier.ClassifierExampleExtractor;
import net.pieroxy.imf.detection.classifier.ClassifierLabel;
import net.pieroxy.imf.detection.classifier.HeaderClassifierTrainer;
import net.pieroxy.imf.detection.classifier.HeaderFeatureGenerator;
import net.pieroxy.imf.config.MailFilterRuleMatcherConfiguration;
import net.pieroxy.imf.rules.RuleContext;
import net.pieroxy.imf.rules.matchers.MatchResult;
import net.pieroxy.imf.rules.matchers.Matcher;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.util.ext.ExtensionLoader;

import javax.mail.Message;
import javax.mail.MessagingException;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.logging.Level;

/**
 * Classifies a message via the model trained by
 * {@link HeaderClassifierTrainer} — structured header-derived
 * features (sender/recipient domains, In-Reply-To, List-Id, Precedence, Return-Path/Reply-To
 * alignment with From...), not the subject text (see {@link SubjectClassifierMatcher}, which
 * this is meant to run alongside rather than replace — see
 * {@code net.pieroxy.imf.config.MailFilterRuleConfiguration#isKeepProcessing()} for comparing
 * the two without either one blocking the mail's normal handling).
 * <p>
 * Same contract as {@link SubjectClassifierMatcher} otherwise: not learnable by example (the
 * config key is a probability threshold like {@code ">0.9"}, not a value pulled from a message),
 * and reuses its {@code ">"}/{@code ">="}/{@code "<"}/{@code "<="} threshold parsing via
 * {@link ReputationThreshold} — same format, same package, no reason to duplicate it a third
 * time.
 */
public class HeaderClassifierMatcher extends Matcher {
  private static final String[] NO_TOKENS = new String[0];

  static {
    // A model file loaded back from disk (loadCategorizer() below, unlike training which builds
    // the factory in memory) instantiates its FeatureGenerator by class name via reflection, and
    // OpenNLP refuses that for any package it doesn't already trust — without this, loading our
    // own model would throw ExtensionNotLoadedException.
    ExtensionLoader.registerAllowedPackage(HeaderFeatureGenerator.class.getPackageName());
  }

  private ReputationThreshold threshold;
  private File modelFile;

  private DocumentCategorizerME categorizer;
  private long loadedModelMtime = -1;
  private boolean loggedInactive;

  @Override
  protected void bindContext(RuleContext context) {
    modelFile = context.headerModelFile();
  }

  @Override
  public void setConfig(MailFilterRuleMatcherConfiguration config) {
    super.setConfig(config);
    threshold = ReputationThreshold.parse(config.getKey(), "HEADER_CLASSIFIER_EQUALS");
    // Same reasoning as SubjectClassifierMatcher: check/log the state right away rather than
    // waiting for the first message, since MailAccount builds the rule catalog at startup
    // precisely so this is known immediately.
    loadCategorizer();
  }

  @Override
  public MatchResult matches(Message message) throws MessagingException {
    DocumentCategorizerME model = loadCategorizer();
    if (model == null) {
      return notMatched();
    }

    ClassifierExample example = ClassifierExampleExtractor.extract(message, null, Instant.now());
    double[] outcome = model.categorize(NO_TOKENS, Map.of(HeaderFeatureGenerator.EXAMPLE_KEY, example));
    double spamScore = outcome[model.getIndex(ClassifierLabel.SPAM.name())];
    boolean matched = threshold.test(spamScore);
    getLogger().fine(() -> "header spam score=" + spamScore + " against " + threshold
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
      } catch (IOException e) {
        getLogger().log(Level.WARNING, "Failed to load classifier model " + modelFile, e);
        categorizer = null;
      }
    }
    return categorizer;
  }

  private void logInactiveOnce(String reason) {
    if (!loggedInactive) {
      getLogger().info("HeaderClassifierMatcher inactive: " + reason);
      loggedInactive = true;
    }
  }
}
