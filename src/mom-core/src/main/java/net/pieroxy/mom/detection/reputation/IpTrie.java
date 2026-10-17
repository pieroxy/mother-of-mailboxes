package net.pieroxy.mom.detection.reputation;

/**
 * Binary trie for IPv4 CIDR ranges: one node per bit, most significant to least significant (32
 * levels max), for a lookup bounded to 32 comparisons regardless of how many ranges are
 * registered — unlike {@link IpReputationList}, which compares the tested IP against
 * <em>every</em> range in the list (O(n)). See {@code IpTrieBenchmark} for the real CPU/memory
 * measurement of both approaches on a real dataset (Spamhaus DROP).
 * <p>
 * Only two possible children per node (bit 0 or 1): no need for binary search or a hash table
 * the way a string-keyed tree would, two direct references are enough and already optimal.
 * <p>
 * A node reached during the descent and marked "terminal" means: all the high-order bits walked
 * so far match a registered CIDR block, so any IP passing through this node is covered,
 * whatever its remaining bits — no need to go deeper. That's exactly CIDR semantics (a shorter
 * prefix covers a wider space), and it stays correct regardless of insertion order: a wide block
 * added after a narrower one it contains does extend the coverage (see {@code IpTrieTest}).
 * <p>
 * IPv4 only for now, like {@link CidrRange} — the same principle extends directly to IPv6 with
 * 128 levels instead of 32.
 */
public class IpTrie {
  private final Node root = new Node();

  private static final class Node {
    private Node zero;
    private Node one;
    private boolean terminal;
  }

  /** Registers the CIDR block whose prefix is the prefixLength high-order bits of ip. */
  public void add(long ip, int prefixLength) {
    Node node = root;
    for (int i = 0; i < prefixLength; i++) {
      if (node.terminal) return; // already covered by a wider block registered earlier: nothing to add
      boolean bit = ((ip >>> (31 - i)) & 1L) != 0;
      Node next = bit ? node.one : node.zero;
      if (next == null) {
        next = new Node();
        if (bit) {
          node.one = next;
        } else {
          node.zero = next;
        }
      }
      node = next;
    }
    node.terminal = true;
  }

  public boolean contains(long ip) {
    Node node = root;
    for (int i = 0; i < 32; i++) {
      if (node.terminal) return true;
      boolean bit = ((ip >>> (31 - i)) & 1L) != 0;
      node = bit ? node.one : node.zero;
      if (node == null) return false;
    }
    return node.terminal;
  }
}
