package net.pieroxy.mom.api.implementations.stats;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * {@link StatsApi#byType} merges the per-detail entries {@code StatsReader} produces (one per
 * distinct sender address, domain, reputation score, ...) back down to one entry per matcher
 * type, so a matcher whose key fragments heavily across many values (e.g. FROM_ADDRESS_EQUALS,
 * one entry per sender) isn't underrepresented in a top-N ranking against one that doesn't
 * (e.g. FROM_DOMAIN_EQUALS, naturally coarser).
 */
public class StatsApiByTypeTest {
  @Test
  public void mergesEntriesThatOnlyDifferByTheirParenthesizedDetail() {
    Map<String, Long> counts = new LinkedHashMap<>();
    counts.put("FromAddressMatcher(a@spam.example.com)", 3L);
    counts.put("FromAddressMatcher(b@spam.example.com)", 5L);
    counts.put("FromDomainMatcher(newsletter.example.com)", 10L);

    Map<String, Long> byType = StatsApi.byType(counts);

    assertEquals(2, byType.size());
    assertEquals(8L, (long) byType.get("FromAddressMatcher"));
    assertEquals(10L, (long) byType.get("FromDomainMatcher"));
  }

  @Test
  public void keepsABracketedTagSinceItNamesADifferentMatcherNotAPerMessageValue() {
    Map<String, Long> counts = new LinkedHashMap<>();
    counts.put("IpReputationMatcher[blocklist](score=0.87)", 2L);
    counts.put("IpReputationMatcher[blocklist](score=0.91)", 1L);
    counts.put("IpReputationMatcher[allowlist](score=0.10)", 4L);

    Map<String, Long> byType = StatsApi.byType(counts);

    assertEquals(2, byType.size());
    assertEquals(3L, (long) byType.get("IpReputationMatcher[blocklist]"));
    assertEquals(4L, (long) byType.get("IpReputationMatcher[allowlist]"));
  }

  @Test
  public void leavesAMatcherWithNoParenthesizedDetailUnchanged() {
    Map<String, Long> counts = new LinkedHashMap<>();
    counts.put("SomeMatcherWithNoDetail", 6L);

    Map<String, Long> byType = StatsApi.byType(counts);

    assertEquals(6L, (long) byType.get("SomeMatcherWithNoDetail"));
    assertFalse(byType.containsKey("SomeMatcherWithNoDetail()"));
  }
}
