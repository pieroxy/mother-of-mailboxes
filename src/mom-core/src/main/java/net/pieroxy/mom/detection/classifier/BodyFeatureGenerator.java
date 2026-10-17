package net.pieroxy.mom.detection.classifier;

import opennlp.tools.doccat.FeatureGenerator;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * One derived feature on top of the body text's own bag-of-words features (see
 * {@code BodyClassifierTrainer}, which combines this with
 * {@code opennlp.tools.doccat.BagOfWordsFeatureGenerator}): whether the body text came from an
 * HTML part, a plain-text part, or neither ({@link ClassifierExample#getBodySource()}) — an
 * image-only or attachment-only message isn't "body text we happened not to extract anything
 * useful from", it's a different kind of message, and stripping HTML down to text erases exactly
 * this distinction, so a bag-of-words generator over the text alone can't recover it (an HTML
 * message and a plain-text message with identical wording produce identical tokens).
 * <p>
 * Same {@code extraInformation}-based wiring as {@link HeaderFeatureGenerator}: the actual
 * {@link ClassifierExample} travels under {@link #EXAMPLE_KEY}, set by {@code BodyClassifierTrainer}
 * (training) and {@code BodyClassifierMatcher} (inference) alike. Same {@code (absent)} sentinel
 * convention too — never silently skipped when there's no body at all.
 */
public class BodyFeatureGenerator implements FeatureGenerator {
  public static final String EXAMPLE_KEY = "example";
  private static final String ABSENT = "(absent)";

  @Override
  public Collection<String> extractFeatures(String[] text, Map<String, Object> extraInformation) {
    Object raw = extraInformation == null ? null : extraInformation.get(EXAMPLE_KEY);
    if (!(raw instanceof ClassifierExample example)) {
      return List.of();
    }
    String bodySource = example.getBodySource();
    return List.of("bodySource=" + (bodySource == null || bodySource.isBlank() ? ABSENT : bodySource));
  }
}
