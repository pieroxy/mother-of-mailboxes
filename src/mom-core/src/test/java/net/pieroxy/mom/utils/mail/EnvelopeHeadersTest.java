package net.pieroxy.mom.utils.mail;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class EnvelopeHeadersTest {
  @Test
  public void normalizesTrimmedAndDeduplicatedIgnoringCase() {
    assertEquals(List.of("Delivered-To", "X-Original-To"),
        EnvelopeHeaders.normalize(Arrays.asList(" Delivered-To ", "X-Original-To", "delivered-to", " ", null)));
    assertEquals(List.of(), EnvelopeHeaders.normalize(null));
  }

  @Test(expected = IllegalArgumentException.class)
  public void refusesAColon() {
    EnvelopeHeaders.normalize(List.of("Delivered-To:"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void refusesASpace() {
    EnvelopeHeaders.normalize(List.of("Delivered To"));
  }
}
