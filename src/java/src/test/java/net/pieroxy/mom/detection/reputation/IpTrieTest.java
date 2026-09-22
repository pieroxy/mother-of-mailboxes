package net.pieroxy.mom.detection.reputation;

import net.pieroxy.mom.detection.reputation.CidrRange;
import net.pieroxy.mom.detection.reputation.IpTrie;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IpTrieTest {

  private static long ip(String s) {
    return CidrRange.ipToLong(s);
  }

  @Test
  public void exactHostOnlyMatchesItself() {
    IpTrie trie = new IpTrie();
    trie.add(ip("1.2.3.4"), 32);

    assertTrue(trie.contains(ip("1.2.3.4")));
    assertFalse(trie.contains(ip("1.2.3.5")));
  }

  @Test
  public void cidrBlockCoversItsWholeRange() {
    IpTrie trie = new IpTrie();
    trie.add(ip("10.0.0.0"), 24);

    assertTrue(trie.contains(ip("10.0.0.0")));
    assertTrue(trie.contains(ip("10.0.0.255")));
    assertFalse(trie.contains(ip("10.0.1.0")));
  }

  @Test
  public void slashZeroCoversEverything() {
    IpTrie trie = new IpTrie();
    trie.add(ip("0.0.0.0"), 0);

    assertTrue(trie.contains(ip("1.2.3.4")));
    assertTrue(trie.contains(ip("255.255.255.255")));
  }

  @Test
  public void broaderBlockAddedAfterANarrowerOneStillExtendsCoverage() {
    IpTrie trie = new IpTrie();
    trie.add(ip("10.0.0.4"), 32);
    trie.add(ip("10.0.0.0"), 24);

    assertTrue(trie.contains(ip("10.0.0.4"))); // the original /32
    assertTrue(trie.contains(ip("10.0.0.200"))); // covered only by the /24 added afterward
    assertFalse(trie.contains(ip("10.0.1.1")));
  }

  @Test
  public void narrowerBlockAddedAfterABroaderOneChangesNothing() {
    IpTrie trie = new IpTrie();
    trie.add(ip("10.0.0.0"), 24);
    trie.add(ip("10.0.0.4"), 32); // already covered by the /24

    assertTrue(trie.contains(ip("10.0.0.4")));
    assertTrue(trie.contains(ip("10.0.0.200")));
    assertFalse(trie.contains(ip("10.0.1.1")));
  }

  @Test
  public void distinctNonOverlappingBlocksDoNotInterfere() {
    IpTrie trie = new IpTrie();
    trie.add(ip("1.2.3.0"), 24);
    trie.add(ip("5.6.7.0"), 24);

    assertTrue(trie.contains(ip("1.2.3.42")));
    assertTrue(trie.contains(ip("5.6.7.42")));
    assertFalse(trie.contains(ip("9.9.9.9")));
  }

  @Test
  public void emptyTrieContainsNothing() {
    IpTrie trie = new IpTrie();
    assertFalse(trie.contains(ip("1.2.3.4")));
  }
}
