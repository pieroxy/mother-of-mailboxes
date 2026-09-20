package net.pieroxy.imf.detection.classifier;

import net.pieroxy.imf.detection.classifier.BodyFeatureGenerator;
import net.pieroxy.imf.detection.classifier.ClassifierExample;
import org.junit.Test;

import java.util.Collection;
import java.util.Map;

import static org.junit.Assert.assertTrue;

public class BodyFeatureGeneratorTest {
  private final BodyFeatureGenerator generator = new BodyFeatureGenerator();
  private static final String[] NO_TOKENS = new String[0];

  private Collection<String> featuresFor(ClassifierExample example) {
    return generator.extractFeatures(NO_TOKENS, Map.of(BodyFeatureGenerator.EXAMPLE_KEY, example));
  }

  @Test
  public void returnsNoFeaturesWhenExtraInformationHasNoExample() {
    assertTrue(generator.extractFeatures(NO_TOKENS, Map.of()).isEmpty());
    assertTrue(generator.extractFeatures(NO_TOKENS, null).isEmpty());
  }

  @Test
  public void bodySourceIsAbsentWhenThereIsNoBodySourceAtAll() {
    ClassifierExample e = new ClassifierExample();

    assertTrue(featuresFor(e).contains("bodySource=(absent)"));
  }

  @Test
  public void bodySourceIsAbsentForABlankValue() {
    ClassifierExample e = new ClassifierExample();
    e.setBodySource("   ");

    assertTrue(featuresFor(e).contains("bodySource=(absent)"));
  }

  @Test
  public void bodySourceIsHtmlWhenTheBodyCameFromAnHtmlPart() {
    ClassifierExample e = new ClassifierExample();
    e.setBodyText("Some actual content");
    e.setBodySource("html");

    assertTrue(featuresFor(e).contains("bodySource=html"));
  }

  @Test
  public void bodySourceIsPlainWhenTheBodyCameFromAPlainTextPart() {
    ClassifierExample e = new ClassifierExample();
    e.setBodyText("Some actual content");
    e.setBodySource("plain");

    assertTrue(featuresFor(e).contains("bodySource=plain"));
  }
}
