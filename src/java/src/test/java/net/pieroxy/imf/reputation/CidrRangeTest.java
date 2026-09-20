package net.pieroxy.imf.reputation;

import net.pieroxy.imf.detection.reputation.CidrRange;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CidrRangeTest {

  @Test
  public void bareIpIsTreatedAsSlash32() {
    CidrRange range = CidrRange.parse("1.2.3.4");
    assertTrue(range.contains(CidrRange.ipToLong("1.2.3.4")));
    assertFalse(range.contains(CidrRange.ipToLong("1.2.3.5")));
  }

  @Test
  public void cidrBlockCoversItsWholeRange() {
    CidrRange range = CidrRange.parse("10.0.0.0/24");
    assertTrue(range.contains(CidrRange.ipToLong("10.0.0.0")));
    assertTrue(range.contains(CidrRange.ipToLong("10.0.0.255")));
    assertFalse(range.contains(CidrRange.ipToLong("10.0.1.0")));
  }

  @Test
  public void slashZeroCoversEverything() {
    CidrRange range = CidrRange.parse("0.0.0.0/0");
    assertTrue(range.contains(CidrRange.ipToLong("1.2.3.4")));
    assertTrue(range.contains(CidrRange.ipToLong("255.255.255.255")));
  }

  @Test(expected = IllegalArgumentException.class)
  public void invalidPrefixLengthIsRejected() {
    CidrRange.parse("1.2.3.4/33");
  }

  @Test(expected = IllegalArgumentException.class)
  public void notAnIpIsRejected() {
    CidrRange.ipToLong("not-an-ip");
  }

  @Test(expected = IllegalArgumentException.class)
  public void octetOutOfRangeIsRejected() {
    CidrRange.ipToLong("1.2.3.256");
  }

  @Test
  public void ipToLongIsOrderedNumerically() {
    assertTrue(CidrRange.ipToLong("1.2.3.5") > CidrRange.ipToLong("1.2.3.4"));
    assertEquals(CidrRange.ipToLong("0.0.0.1"), 1L);
    assertEquals(CidrRange.ipToLong("0.0.1.0"), 256L);
  }
}
