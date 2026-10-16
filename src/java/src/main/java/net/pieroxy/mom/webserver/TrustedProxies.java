package net.pieroxy.mom.webserver;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The reverse proxies whose {@code X-Forwarded-For} header MOM believes: a list of IP addresses and
 * CIDR ranges, IPv4 or IPv6. Empty: the header is ignored and the connecting address is the client.
 * <p>
 * Only literal addresses are ever parsed, never host names: a forged header can't trigger a DNS
 * lookup.
 */
public final class TrustedProxies {
  public static final TrustedProxies NONE = new TrustedProxies(List.of(), List.of());

  private static final Pattern IPV4 = Pattern.compile("^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$");
  private static final Pattern IPV4_WITH_PORT = Pattern.compile("^([\\d.]+):\\d+$");
  private static final Pattern BRACKETED_IPV6 = Pattern.compile("^\\[([0-9a-fA-F:.]+)](?::\\d+)?$");
  private static final Pattern IPV6 = Pattern.compile("^[0-9a-fA-F:.]*:[0-9a-fA-F:.]*$");

  private record Range(byte[] network, int prefixLength) {
    boolean contains(byte[] address) {
      if (address.length != network.length) return false;
      int fullBytes = prefixLength / 8;
      for (int i = 0; i < fullBytes; i++) {
        if (address[i] != network[i]) return false;
      }
      int remainingBits = prefixLength % 8;
      if (remainingBits == 0) return true;
      int mask = 0xff << (8 - remainingBits) & 0xff;
      return (address[fullBytes] & mask) == (network[fullBytes] & mask);
    }
  }

  private final List<String> entries;
  private final List<Range> ranges;

  private TrustedProxies(List<String> entries, List<Range> ranges) {
    this.entries = entries;
    this.ranges = ranges;
  }

  /** @throws IllegalArgumentException naming the first entry that isn't an IP address or a CIDR range. */
  public static TrustedProxies parse(List<String> entries) {
    if (entries == null || entries.isEmpty()) return NONE;
    List<String> kept = new ArrayList<>();
    List<Range> ranges = new ArrayList<>();
    for (String raw : entries) {
      String entry = raw == null ? "" : raw.trim();
      int slash = entry.indexOf('/');
      InetAddress address = literal(slash < 0 ? entry : entry.substring(0, slash));
      if (address == null) throw invalid(raw);
      int maxPrefix = address.getAddress().length * 8;
      int prefix = maxPrefix;
      if (slash >= 0) {
        try {
          prefix = Integer.parseInt(entry.substring(slash + 1));
        } catch (NumberFormatException e) {
          throw invalid(raw);
        }
        if (prefix < 0 || prefix > maxPrefix) throw invalid(raw);
      }
      kept.add(entry);
      ranges.add(new Range(address.getAddress(), prefix));
    }
    return new TrustedProxies(List.copyOf(kept), List.copyOf(ranges));
  }

  private static IllegalArgumentException invalid(String entry) {
    return new IllegalArgumentException("Trusted proxy \"" + entry + "\" is neither an IP address nor a CIDR range (e.g. 127.0.0.1, 172.18.0.0/16, ::1).");
  }

  public boolean isEmpty() {
    return ranges.isEmpty();
  }

  public List<String> entries() {
    return entries;
  }

  public boolean isTrusted(String address) {
    InetAddress parsed = literal(address);
    return parsed != null && isTrusted(parsed);
  }

  private boolean isTrusted(InetAddress address) {
    byte[] bytes = address.getAddress();
    return ranges.stream().anyMatch(range -> range.contains(bytes));
  }

  /**
   * The client's address: {@code remoteAddr} unless it's a trusted proxy, in which case
   * {@code xForwardedFor} is walked from the right — each proxy appends the address it received the
   * request from — skipping trusted proxies; the first untrusted address is the client. The start
   * of the header is whatever the client sent, so it's only reached when every hop after it is
   * trusted. A malformed entry stops the walk at the last address read.
   *
   * @param xForwardedFor every {@code X-Forwarded-For} header, comma-joined; null if none.
   */
  public String clientAddress(String remoteAddr, String xForwardedFor) {
    if (isEmpty() || xForwardedFor == null || xForwardedFor.isBlank() || !isTrusted(remoteAddr)) return remoteAddr;
    String client = remoteAddr;
    String[] hops = xForwardedFor.split(",");
    for (int i = hops.length - 1; i >= 0; i--) {
      InetAddress hop = literal(withoutPort(hops[i].trim()));
      if (hop == null) break;
      client = hop.getHostAddress();
      if (!isTrusted(hop)) break;
    }
    return client;
  }

  private static String withoutPort(String hop) {
    var bracketed = BRACKETED_IPV6.matcher(hop);
    if (bracketed.matches()) return bracketed.group(1);
    var withPort = IPV4_WITH_PORT.matcher(hop);
    return withPort.matches() ? withPort.group(1) : hop;
  }

  /** An IPv4 or IPv6 literal, null for anything else — never resolved through DNS. */
  private static InetAddress literal(String s) {
    if (s == null) return null;
    if (!IPV4.matcher(s).matches() && !IPV6.matcher(s).matches()) return null;
    try {
      return InetAddress.getByName(s);
    } catch (UnknownHostException e) {
      return null;
    }
  }
}
