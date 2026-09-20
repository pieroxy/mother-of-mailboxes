package net.pieroxy.imf.detection.reputation;

/**
 * An IPv4/CIDR block ("1.2.3.0/24", or "1.2.3.4" treated as a /32), as found in
 * {@link ReputationListType#IP_CIDR}-type lists. IPv6 not supported for now — an IPv6 address
 * will never match any block here (see {@link IpReputationList}).
 */
final class CidrRange {
  private final long start;
  private final long end;
  private final int prefixLength;

  private CidrRange(long start, long end, int prefixLength) {
    this.start = start;
    this.end = end;
    this.prefixLength = prefixLength;
  }

  static CidrRange parse(String text) {
    int slash = text.indexOf('/');
    String ipPart = slash < 0 ? text : text.substring(0, slash);
    int prefixLength = slash < 0 ? 32 : Integer.parseInt(text.substring(slash + 1));
    if (prefixLength < 0 || prefixLength > 32) {
      throw new IllegalArgumentException("Invalid IPv4 prefix length: " + text);
    }
    long ip = ipToLong(ipPart);
    long mask = prefixLength == 0 ? 0 : (0xFFFFFFFFL << (32 - prefixLength)) & 0xFFFFFFFFL;
    long start = ip & mask;
    long end = start | (~mask & 0xFFFFFFFFL);
    return new CidrRange(start, end, prefixLength);
  }

  /** "Manual"/reference check, used by tests — {@link IpTrie} is what's actually used in production, see {@link IpReputationList}. */
  boolean contains(long ip) {
    return ip >= start && ip <= end;
  }

  long start() {
    return start;
  }

  int prefixLength() {
    return prefixLength;
  }

  static long ipToLong(String ip) {
    String[] parts = ip.split("\\.", -1);
    if (parts.length != 4) {
      throw new IllegalArgumentException("Not an IPv4 address: " + ip);
    }
    long result = 0;
    for (String part : parts) {
      int octet;
      try {
        octet = Integer.parseInt(part);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Not an IPv4 address: " + ip);
      }
      if (octet < 0 || octet > 255) {
        throw new IllegalArgumentException("Not an IPv4 address: " + ip);
      }
      result = (result << 8) | octet;
    }
    return result;
  }
}
