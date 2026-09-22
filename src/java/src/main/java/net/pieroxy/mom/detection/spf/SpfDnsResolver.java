package net.pieroxy.mom.detection.spf;

import java.util.List;

/**
 * Abstraction of the DNS queries {@link SpfEvaluator} needs. Lets the SPF algorithm be tested
 * with in-memory answers, without any network or real DNS server.
 * <p>
 * All methods return an empty list when the domain or record type doesn't exist (NXDOMAIN /
 * NODATA): that's not an error, it's a normal DNS outcome. {@link SpfDnsException} is reserved
 * for temporary failures (timeout, SERVFAIL...).
 */
public interface SpfDnsResolver {
  /** Raw content of the domain's TXT records (each record concatenated into one String). */
  List<String> lookupTxt(String domain) throws SpfDnsException;

  /** IPv4 addresses of the domain, as literals ("1.2.3.4"). */
  List<String> lookupA(String domain) throws SpfDnsException;

  /** IPv6 addresses of the domain, as literals. */
  List<String> lookupAaaa(String domain) throws SpfDnsException;

  /** Hostnames of the domain's MX records. */
  List<String> lookupMx(String domain) throws SpfDnsException;
}
