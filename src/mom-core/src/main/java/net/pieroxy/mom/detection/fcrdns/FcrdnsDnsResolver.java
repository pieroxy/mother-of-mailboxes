package net.pieroxy.mom.detection.fcrdns;

import java.util.List;

/**
 * Abstraction of the DNS queries {@link FcrdnsEvaluator} needs: the PTR (reverse) lookup of an
 * IP, and the forward (A/AAAA) lookup of a hostname to confirm the PTR. An empty list means
 * "nothing at this name", not an error; {@link FcrdnsDnsException} is reserved for temporary
 * failures (timeout, SERVFAIL...).
 */
public interface FcrdnsDnsResolver {
  /** PTR hostnames for this IP (literal, IPv4 or IPv6). */
  List<String> lookupPtr(String ip) throws FcrdnsDnsException;

  /** IPv4 addresses for this hostname. */
  List<String> lookupA(String hostname) throws FcrdnsDnsException;

  /** IPv6 addresses for this hostname. */
  List<String> lookupAaaa(String hostname) throws FcrdnsDnsException;
}
