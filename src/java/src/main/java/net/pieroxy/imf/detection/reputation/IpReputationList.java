package net.pieroxy.imf.detection.reputation;

import java.util.List;

/**
 * Stored as an {@link IpTrie} (one node per bit, lookup bounded to 32 comparisons) rather than a
 * linear scan of the CIDR blocks: measured on real reputation lists (Spamhaus DROP, 1708
 * blocks; FireHOL blocklist_de_mail, 12341 blocks), the trie is 37x to 225x faster to look up
 * than walking the list — the gap widens with list size, since the linear scan is O(n) while the
 * trie stays O(32) — for a negligible memory cost (a few MB). See
 * {@code net.pieroxy.imf.standalone.IpTrieBenchmark}.
 */
final class IpReputationList implements ReputationList {
  private final IpTrie trie;

  IpReputationList(List<CidrRange> ranges) {
    this.trie = new IpTrie();
    for (CidrRange range : ranges) {
      trie.add(range.start(), range.prefixLength());
    }
  }

  @Override
  public boolean contains(String value) {
    long ip;
    try {
      ip = CidrRange.ipToLong(value);
    } catch (IllegalArgumentException e) {
      return false; // not an IPv4 address (e.g. IPv6, not supported): never matches an IP_CIDR list
    }
    return trie.contains(ip);
  }
}
