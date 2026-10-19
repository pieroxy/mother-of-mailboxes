package net.pieroxy.mom.diagnostics;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Checks that a sending domain can also receive mail — its MX records (RFC 5321 §5.1): that it has
 * some, that it doesn't refuse all mail (null MX, RFC 7505), and that each points to a real host
 * name (not an IP address, nor an alias) resolving to public addresses. Replies and bounces need
 * it, and some receivers distrust mail from a domain that can't be answered. DNS only: doesn't need
 * a test message.
 */
public class MxCheck {
  private static final Pattern IP_LITERAL = Pattern.compile("^\\[?[0-9a-fA-F:.]+]?$");

  private final MxDnsResolver resolver;

  public MxCheck(MxDnsResolver resolver) {
    this.resolver = resolver;
  }

  public DeliveryCheckResultDto run(String domain) {
    DeliveryCheckResultDto result = new DeliveryCheckResultDto(DeliveryCheck.MX).fact("Domain", domain);
    List<MxDnsResolver.Mx> mxs;
    try {
      mxs = new ArrayList<>(resolver.lookupMx(domain));
    } catch (MxDnsResolver.DnsLookupException e) {
      return result.finding(CheckStatus.WARNING, "Could not query the DNS for " + domain + "'s MX records (" + e.getMessage()
          + "). Try again later.").conclude("");
    }
    if (mxs.isEmpty()) return noMx(result, domain);

    mxs.sort(Comparator.comparingInt(MxDnsResolver.Mx::priority));
    result.fact("MX records", mxs.stream().map(mx -> mx.priority() + " " + (mx.host().isEmpty() ? "." : mx.host()))
        .collect(Collectors.joining(", ")));
    if (mxs.stream().anyMatch(mx -> mx.host().isEmpty())) {
      return result.finding(CheckStatus.ERROR, domain + " has a null MX record (\".\"): it declares that it accepts no mail at all "
          + "(RFC 7505). Replies and bounces are refused, and receivers are told to reject mail sent from it. Replace it with "
          + "your mail server's MX record.").conclude("");
    }

    int working = 0;
    for (MxDnsResolver.Mx mx : mxs) {
      if (checkHost(result, mx)) working++;
    }
    if (working == 0) {
      result.finding(CheckStatus.ERROR, "None of " + domain + "'s MX records leads to a reachable server: replies and bounces "
          + "can't be delivered to it.");
    } else {
      result.finding(CheckStatus.OK, domain + " can receive mail: " + working + " of its " + mxs.size()
          + (mxs.size() == 1 ? " MX record leads" : " MX records lead") + " to a public address.");
    }
    return result.conclude(domain + " has valid MX records: it can receive replies and bounces.");
  }

  private DeliveryCheckResultDto noMx(DeliveryCheckResultDto result, String domain) {
    List<String> addresses;
    try {
      addresses = addressesOf(domain);
    } catch (MxDnsResolver.DnsLookupException e) {
      addresses = List.of();
    }
    if (!addresses.isEmpty()) {
      result.fact("Addresses of " + domain, String.join(", ", addresses));
      return result.finding(CheckStatus.WARNING, domain + " has no MX record: servers fall back to its own address ("
          + String.join(", ", addresses) + "), which only works if that machine receives mail. Add an MX record naming your "
          + "mail server.").conclude("");
    }
    return result.finding(CheckStatus.ERROR, domain + " has no MX record, nor an address: it can't receive mail. Replies and "
        + "bounces are lost, and some receivers reject mail from a domain that can't be answered. Add an MX record naming your "
        + "mail server.").conclude("");
  }

  /** @return whether this MX leads somewhere mail can be delivered. */
  private boolean checkHost(DeliveryCheckResultDto result, MxDnsResolver.Mx mx) {
    String host = mx.host().toLowerCase(Locale.ROOT);
    if (IP_LITERAL.matcher(host).matches()) {
      result.finding(CheckStatus.ERROR, "The MX record \"" + mx.host() + "\" is an IP address: MX records must name a host, many "
          + "servers ignore this one. Point it to a name that resolves to that address.");
      return false;
    }
    List<String> addresses;
    try {
      List<String> cname = resolver.lookupCname(host);
      if (!cname.isEmpty()) {
        result.finding(CheckStatus.WARNING, "The MX record " + host + " is an alias (CNAME to " + cname.get(0) + "): forbidden by "
            + "RFC 2181, and some servers refuse to deliver to it. Point the MX record straight to " + cname.get(0) + ".");
      }
      addresses = addressesOf(host);
    } catch (MxDnsResolver.DnsLookupException e) {
      result.finding(CheckStatus.WARNING, "Could not resolve the MX host " + host + " (" + e.getMessage() + "). Try again later.");
      return false;
    }
    if (addresses.isEmpty()) {
      result.finding(CheckStatus.ERROR, "The MX host " + host + " doesn't resolve to any address: mail can't be delivered through it.");
      return false;
    }
    result.fact(host, String.join(", ", addresses));
    List<String> nonPublic = addresses.stream().filter(MxCheck::isNonPublic).toList();
    if (nonPublic.size() == addresses.size()) {
      result.finding(CheckStatus.ERROR, "The MX host " + host + " only resolves to private or reserved addresses ("
          + String.join(", ", nonPublic) + "): servers on the internet can't reach it.");
      return false;
    }
    if (!nonPublic.isEmpty()) {
      result.finding(CheckStatus.WARNING, "The MX host " + host + " also resolves to private or reserved addresses ("
          + String.join(", ", nonPublic) + "), which servers on the internet can't reach: remove them from its DNS.");
    }
    return true;
  }

  private List<String> addressesOf(String name) throws MxDnsResolver.DnsLookupException {
    List<String> addresses = new ArrayList<>(resolver.lookupA(name));
    addresses.addAll(resolver.lookupAaaa(name));
    return addresses;
  }

  /** Loopback, private, link-local, unspecified, unique-local (fc00::/7) or documentation/test ranges. */
  static boolean isNonPublic(String ip) {
    try {
      InetAddress a = InetAddress.getByName(ip);
      if (a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress() || a.isAnyLocalAddress()) return true;
      byte[] b = a.getAddress();
      if (b.length == 16) return (b[0] & 0xfe) == 0xfc;
      int first = b[0] & 0xff, second = b[1] & 0xff;
      return first == 0 || (first == 100 && second >= 64 && second < 128) // 0/8, carrier-grade NAT 100.64/10
          || first >= 224; // multicast, reserved
    } catch (UnknownHostException e) {
      return true;
    }
  }
}
