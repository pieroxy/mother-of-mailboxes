package net.pieroxy.mom.detection.classifier;

import net.pieroxy.mom.detection.classifier.ClassifierExample;
import net.pieroxy.mom.detection.classifier.HeaderFeatureGenerator;
import org.junit.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HeaderFeatureGeneratorTest {
  private final HeaderFeatureGenerator generator = new HeaderFeatureGenerator();
  private static final String[] NO_TOKENS = new String[0];

  private Collection<String> featuresFor(ClassifierExample example) {
    return generator.extractFeatures(NO_TOKENS, Map.of(HeaderFeatureGenerator.EXAMPLE_KEY, example));
  }

  private static ClassifierExample blankExample() {
    ClassifierExample e = new ClassifierExample();
    e.setFrom(List.of());
    e.setTo(List.of());
    return e;
  }

  @Test
  public void returnsNoFeaturesWhenExtraInformationHasNoExample() {
    assertTrue(generator.extractFeatures(NO_TOKENS, Map.of()).isEmpty());
    assertTrue(generator.extractFeatures(NO_TOKENS, null).isEmpty());
  }

  @Test
  public void emitsOneWordFeaturePerDisplayNameWordLowercased() {
    ClassifierExample e = blankExample();
    e.setFromDisplayName("Alice Smith");

    Collection<String> features = featuresFor(e);
    assertTrue(features.contains("fromNameWord=alice"));
    assertTrue(features.contains("fromNameWord=smith"));
  }

  @Test
  public void emitsNoNameWordFeaturesWhenDisplayNameIsAbsent() {
    Collection<String> features = featuresFor(blankExample());
    assertFalse(features.stream().anyMatch(f -> f.startsWith("fromNameWord=")));
  }

  @Test
  public void emitsBooleanFeaturesExplicitlyEvenWhenFalse() {
    ClassifierExample e = blankExample(); // reply and listUnsubscribePresent default to false
    Collection<String> features = featuresFor(e);
    assertTrue("false must still be an explicit feature, not just absent", features.contains("reply=false"));
    assertTrue(features.contains("listUnsubscribe=false"));

    e.setReply(true);
    e.setListUnsubscribePresent(true);
    Collection<String> featuresTrue = featuresFor(e);
    assertTrue(featuresTrue.contains("reply=true"));
    assertTrue(featuresTrue.contains("listUnsubscribe=true"));
  }

  @Test
  public void emitsPrecedenceAndListIdRawValuesOrAbsentSentinel() {
    assertTrue(featuresFor(blankExample()).contains("precedence=(absent)"));
    assertTrue(featuresFor(blankExample()).contains("listId=(absent)"));

    ClassifierExample e = blankExample();
    e.setPrecedence("bulk");
    e.setListId("newsletter.example.com");
    Collection<String> features = featuresFor(e);
    assertTrue(features.contains("precedence=bulk"));
    assertTrue(features.contains("listId=newsletter.example.com"));
  }

  @Test
  public void emitsReturnPathAndReplyToDomainsOrAbsentSentinel() {
    ClassifierExample e = blankExample();
    e.setReturnPathDomain("bounce.example.com");
    e.setReplyToDomain("support.example.com");

    Collection<String> features = featuresFor(e);
    assertTrue(features.contains("returnPathDomain=bounce.example.com"));
    assertTrue(features.contains("replyToDomain=support.example.com"));
    assertTrue(featuresFor(blankExample()).contains("returnPathDomain=(absent)"));
    assertTrue(featuresFor(blankExample()).contains("replyToDomain=(absent)"));
  }

  @Test
  public void mismatchFeaturesAreTrueFalseOrUnknown() {
    ClassifierExample unknown = blankExample();
    assertTrue(featuresFor(unknown).contains("returnPathMismatch=unknown"));
    assertTrue(featuresFor(unknown).contains("replyToMismatch=unknown"));

    ClassifierExample mismatched = blankExample();
    mismatched.setReturnPathMismatch(true);
    mismatched.setReplyToMismatch(false);
    Collection<String> features = featuresFor(mismatched);
    assertTrue(features.contains("returnPathMismatch=true"));
    assertTrue(features.contains("replyToMismatch=false"));
  }

  @Test
  public void attachmentCountIsZeroAndExtensionIsAbsentWhenNoAttachments() {
    Collection<String> features = featuresFor(blankExample()); // attachmentExtensions left null
    assertTrue(features.contains("attachmentCount=0"));
    assertTrue(features.contains("attachmentExt=(absent)"));
  }

  @Test
  public void emitsOneAttachmentExtensionFeaturePerDistinctExtension() {
    ClassifierExample e = blankExample();
    e.setAttachmentExtensions(List.of("exe", "exe", "zip"));

    Set<String> features = Set.copyOf(featuresFor(e));
    assertTrue(features.contains("attachmentCount=2-3"));
    assertTrue(features.contains("attachmentExt=exe"));
    assertTrue(features.contains("attachmentExt=zip"));
    long extFeatureCount = features.stream().filter(f -> f.startsWith("attachmentExt=")).count();
    assertEquals("the two 'exe' attachments must dedupe into one feature", 2, extFeatureCount);
  }

  @Test
  public void attachmentCountBucketsLargerCountsTogether() {
    ClassifierExample one = blankExample();
    one.setAttachmentExtensions(List.of("pdf"));
    assertTrue(featuresFor(one).contains("attachmentCount=1"));

    ClassifierExample four = blankExample();
    four.setAttachmentExtensions(List.of("pdf", "pdf", "pdf", "pdf"));
    assertTrue(featuresFor(four).contains("attachmentCount=4+"));
  }

  @Test
  public void dateDeltaIsUnknownWhenEitherDateIsMissing() {
    assertTrue(featuresFor(blankExample()).contains("dateDelta=unknown")); // both missing

    ClassifierExample onlyMailDate = blankExample();
    onlyMailDate.setMailDate("2026-01-01T12:00:00Z");
    assertTrue(featuresFor(onlyMailDate).contains("dateDelta=unknown"));
  }

  @Test
  public void dateDeltaIsNormalForOrdinaryTransitTime() {
    ClassifierExample e = blankExample();
    e.setMailDate("2026-01-01T12:00:00Z");
    e.setReceivedDate("2026-01-01T12:00:30Z"); // received 30s after it claims to have been sent

    assertTrue(featuresFor(e).contains("dateDelta=normal"));
  }

  @Test
  public void dateDeltaIsFutureWhenReceivedBeforeItsClaimedSendTime() {
    ClassifierExample e = blankExample();
    e.setMailDate("2026-01-01T12:00:00Z");
    e.setReceivedDate("2026-01-01T11:00:00Z"); // received an hour before it claims to have been sent

    assertTrue(featuresFor(e).contains("dateDelta=future"));
  }

  @Test
  public void dateDeltaIsStaleWhenReceivedLongAfterItsClaimedSendTime() {
    ClassifierExample e = blankExample();
    e.setMailDate("2026-01-01T12:00:00Z");
    e.setReceivedDate("2026-01-08T12:00:00Z"); // received a full week after its claimed send date

    assertTrue(featuresFor(e).contains("dateDelta=stale"));
  }
}
