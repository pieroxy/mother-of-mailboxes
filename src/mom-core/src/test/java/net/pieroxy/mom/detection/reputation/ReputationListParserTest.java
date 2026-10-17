package net.pieroxy.mom.detection.reputation;

import net.pieroxy.mom.detection.reputation.ReputationListParser;
import net.pieroxy.mom.detection.reputation.ReputationListType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReputationListParserTest {

  @Test
  public void parsesIpCidrEntriesIgnoringCommentsAndBlankLines() {
    String content = "# a comment\n1.2.3.0/24\n\n5.6.7.8\n";
    ReputationListParser.ParseResult result = ReputationListParser.parse("test", ReputationListType.IP_CIDR, content);
    assertTrue(result.list().contains("1.2.3.42"));
    assertTrue(result.list().contains("5.6.7.8"));
    assertFalse(result.list().contains("9.9.9.9"));
    assertEquals(2, result.validCount());
    assertEquals(0, result.invalidCount());
  }

  @Test
  public void semicolonHeaderAndTrailingReferenceAreStrippedLikeSpamhausDrop() {
    // Real format of https://www.spamhaus.org/drop/drop.txt: header comment starting with ";",
    // and each block followed by a "; SBLxxxx" reference on the same line.
    String content = "; (c) 2026 The Spamhaus Project SLU\n"
            + "1.10.16.0/20 ; SBL256894\n"
            + "5.42.92.0/24 ; SBL625300\n";
    ReputationListParser.ParseResult result = ReputationListParser.parse("spamhaus-drop", ReputationListType.IP_CIDR, content);
    assertTrue(result.list().contains("1.10.16.1"));
    assertTrue(result.list().contains("5.42.92.1"));
    assertEquals(2, result.validCount());
    assertEquals(0, result.invalidCount());
  }

  @Test
  public void invalidIpCidrEntryIsSkippedButRestOfListStillLoadsAndCountsAreReported() {
    String content = "not-an-ip\n1.2.3.0/24\nalso-not-an-ip\n";
    ReputationListParser.ParseResult result = ReputationListParser.parse("test", ReputationListType.IP_CIDR, content);
    assertTrue(result.list().contains("1.2.3.1"));
    assertEquals(1, result.validCount());
    assertEquals(2, result.invalidCount());
  }

  @Test
  public void parsesDomainEntriesCaseInsensitively() {
    String content = "# a comment\nBadDomain.example.com\n\nother.example\n";
    ReputationListParser.ParseResult result = ReputationListParser.parse("test", ReputationListType.DOMAIN, content);
    assertTrue(result.list().contains("baddomain.example.com"));
    assertTrue(result.list().contains("OTHER.example"));
    assertFalse(result.list().contains("good.example.com"));
    assertEquals(2, result.validCount());
    assertEquals(0, result.invalidCount());
  }
}
