package net.pieroxy.imf.rules.matchers.implementations;

import net.pieroxy.imf.detection.classifier.ClassifierLabel;
import net.pieroxy.imf.config.MailFilterRuleMatcherConfiguration;
import net.pieroxy.imf.detection.classifier.ClassifierCorpusScanner;
import net.pieroxy.imf.detection.classifier.SubjectClassifierTrainer;
import net.pieroxy.imf.rules.RuleContext;
import net.pieroxy.imf.rules.matchers.MatchResult;
import net.pieroxy.imf.rules.matchers.Matcher;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.tokenize.SimpleTokenizer;

import javax.mail.Message;
import javax.mail.MessagingException;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Classifies the message's subject via the model trained by
 * {@link SubjectClassifierTrainer} on the corpus collected by
 * {@link ClassifierCorpusScanner}. Unlike other "leaf" matchers,
 * there's no learning by dropping an example in imf-rules/ (there's no "key" to extract from a
 * message): learning comes from the corpus, and the config key is a probability threshold
 * (e.g. "&gt;0.9", "&lt;0.1") rather than a value to compare against.
 * <p>
 * To compare confident/unsure without complicating {@link Matcher}'s contract (which stays
 * boolean), two rules at two different thresholds are configured instead of a result with
 * several confidence levels — e.g. "&gt;0.99" for a firm action, "&gt;0.5" for a more cautious
 * one.
 */
public class SubjectClassifierMatcher extends Matcher {
  private final static Pattern THRESHOLD_PATTERN = Pattern.compile("(>=|<=|>|<)\\s*([0-9]*\\.?[0-9]+)");

  private String operator;
  private double threshold;
  private File modelFile;

  private DocumentCategorizerME categorizer;
  private long loadedModelMtime = -1;
  private boolean loggedInactive;

  @Override
  protected void bindContext(RuleContext context) {
    modelFile = context.subjectModelFile();
  }

  @Override
  public void setConfig(MailFilterRuleMatcherConfiguration config) {
    super.setConfig(config);
    String key = config.getKey();
    java.util.regex.Matcher m = THRESHOLD_PATTERN.matcher(key == null ? "" : key.trim());
    if (!m.matches()) {
      throw new IllegalArgumentException("SUBJECT_CLASSIFIER_EQUALS key must look like \">0.9\" or \"<0.1\", got: " + key);
    }
    operator = m.group(1);
    threshold = Double.parseDouble(m.group(2));

    // Checks/logs the state right away (active or not, and why) rather than waiting for the
    // first message inspected — MailAccount builds the rule catalog at startup precisely for
    // this (see MailAccount.run()): on an account that doesn't get anything right away, we'd
    // otherwise never know whether this matcher is operational.
    loadCategorizer();
  }

  @Override
  public MatchResult matches(Message message) throws MessagingException {
    String subject = message.getSubject();
    if (subject == null || subject.isBlank()) {
      getLogger().fine(() -> "no subject on message, no match against " + describeKey());
      return notMatched();
    }

    DocumentCategorizerME model = loadCategorizer();
    if (model == null) {
      return notMatched();
    }

    String[] tokens = SimpleTokenizer.INSTANCE.tokenize(subject);
    Map<String, Double> scores = model.scoreMap(tokens);
    double spamScore = scores.getOrDefault(ClassifierLabel.SPAM.name(), 0.0);
    boolean matched = passesThreshold(spamScore);
    getLogger().fine(() -> "subject spam score=" + spamScore + " against " + describeKey()
        + " -> " + (matched ? "match" : "no match"));
    return matched ? matched("score=" + spamScore) : notMatched();
  }

  private boolean passesThreshold(double score) {
    return switch (operator) {
      case ">" -> score > threshold;
      case ">=" -> score >= threshold;
      case "<" -> score < threshold;
      case "<=" -> score <= threshold;
      default -> throw new IllegalStateException("Unknown operator: " + operator);
    };
  }

  /**
   * Loads/reloads the model if needed. The matcher is only rebuilt when a learned rule changes
   * (see RuleCatalog) — never when this model is retrained, since it isn't learned by folder. So
   * we detect a new model's appearance ourselves via its last-modified date, rather than
   * depending on that invalidation cycle.
   */
  private DocumentCategorizerME loadCategorizer() {
    if (modelFile == null) {
      // Shouldn't happen in normal use (Matcher.build() binds the account's RuleContext before
      // this runs); report it clearly rather than failing further down.
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
        loggedInactive = false; // active again: a future disappearance will be logged again
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
      getLogger().info("SubjectClassifierMatcher inactive: " + reason);
      loggedInactive = true;
    }
  }
}
