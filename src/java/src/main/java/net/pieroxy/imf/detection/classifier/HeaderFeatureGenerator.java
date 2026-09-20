package net.pieroxy.imf.detection.classifier;

import opennlp.tools.doccat.FeatureGenerator;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Structured features from a {@link ClassifierExample}'s headers — not a bag-of-words. Doccat's
 * default is to tokenize free text ({@code opennlp.tools.doccat.BagOfWordsFeatureGenerator}, one
 * word = one feature); here almost none of the signal is free text, it's already-parsed facts
 * (a domain, a boolean, a header's raw value), so each one becomes its own named feature instead
 * of being flattened into a text blob and re-tokenized.
 * <p>
 * Doccat's {@link FeatureGenerator} contract is {@code extractFeatures(String[] text, Map
 * extraInformation)} — the {@code text} array is for token-based generators, unused here; the
 * actual input travels through {@code extraInformation} under {@link #EXAMPLE_KEY}, set by
 * {@link HeaderClassifierTrainer} (training, from the corpus) and by {@code HeaderClassifierMatcher}
 * (inference, from a live message via {@link ClassifierExampleExtractor}) alike — same
 * extraction code either way, so training and matching can never disagree on how a field is
 * derived.
 * <p>
 * Every single-valued field always emits exactly one feature, using {@link #ABSENT} as an
 * explicit sentinel when the underlying header is missing — never silently skipped. Absence is
 * itself a signal (e.g. "no Reply-To at all" is different from "Reply-To agrees with From"), and
 * skipping it would conflate "not examined" with "false" in a model that has no way to tell the
 * two apart otherwise, since doccat's features are presence-based, not numerically valued.
 */
public class HeaderFeatureGenerator implements FeatureGenerator {
  public static final String EXAMPLE_KEY = "example";
  private static final String ABSENT = "(absent)";

  @Override
  public Collection<String> extractFeatures(String[] text, Map<String, Object> extraInformation) {
    Object raw = extraInformation == null ? null : extraInformation.get(EXAMPLE_KEY);
    if (!(raw instanceof ClassifierExample example)) {
      return List.of();
    }

    List<String> features = new ArrayList<>();
    for (String word : words(example.getFromDisplayName())) {
      features.add("fromNameWord=" + word);
    }
    features.add("reply=" + example.isReply());
    features.add("listUnsubscribe=" + example.isListUnsubscribePresent());
    features.add("precedence=" + orAbsent(example.getPrecedence()));
    features.add("listId=" + orAbsent(example.getListId()));
    features.add("returnPathDomain=" + orAbsent(example.getReturnPathDomain()));
    features.add("replyToDomain=" + orAbsent(example.getReplyToDomain()));
    features.add("returnPathMismatch=" + triState(example.getReturnPathMismatch()));
    features.add("replyToMismatch=" + triState(example.getReplyToMismatch()));

    List<String> attachmentExtensions = example.getAttachmentExtensions();
    int attachmentCount = attachmentExtensions == null ? 0 : attachmentExtensions.size();
    features.add("attachmentCount=" + bucketAttachmentCount(attachmentCount));
    if (attachmentCount == 0) {
      features.add("attachmentExt=" + ABSENT);
    } else {
      for (String ext : new LinkedHashSet<>(attachmentExtensions)) {
        features.add("attachmentExt=" + ext);
      }
    }

    features.add("dateDelta=" + bucketDateDelta(example.getMailDate(), example.getReceivedDate()));
    return features;
  }

  /**
   * Bucketed rather than a raw number: doccat's features are presence-based, not numerically
   * valued (see the class javadoc), so "3" and "4" need to fall in the same bucket to be treated
   * as similar evidence instead of two unrelated, individually rare feature values.
   */
  private static String bucketAttachmentCount(int count) {
    if (count == 0) return "0";
    if (count == 1) return "1";
    if (count <= 3) return "2-3";
    return "4+";
  }

  /**
   * How far the sender's self-reported {@code Date:} header (mailDate, forgeable) diverges from
   * when the message actually landed on the server (receivedDate, INTERNALDATE — not forgeable
   * by the sender). A small negative gap (received shortly after the claimed send time) is the
   * normal case; a mail claiming to have been sent *after* it was actually received is
   * impossible in reality and a red flag; a mailDate far in the past relative to receipt is
   * unusual too (a replayed or badly clock-skewed message).
   */
  private static String bucketDateDelta(String mailDate, String receivedDate) {
    if (mailDate == null || receivedDate == null) return "unknown";
    try {
      Duration transit = Duration.between(Instant.parse(mailDate), Instant.parse(receivedDate));
      long transitMinutes = transit.toMinutes(); // received - sent: normally a small positive value
      if (transitMinutes < -5) return "future"; // claims to have been sent after it was received
      if (transitMinutes <= 6 * 60) return "normal"; // ordinary transit time plus some clock skew tolerance
      return "stale"; // received long after its claimed send date
    } catch (DateTimeParseException e) {
      return "unknown";
    }
  }

  private static String orAbsent(String value) {
    return value == null || value.isBlank() ? ABSENT : value;
  }

  private static String triState(Boolean value) {
    return value == null ? "unknown" : value.toString();
  }

  private static List<String> words(String joinedNames) {
    if (joinedNames == null || joinedNames.isBlank()) return List.of();
    return List.of(joinedNames.toLowerCase(Locale.ROOT).split("\\s+"));
  }
}
