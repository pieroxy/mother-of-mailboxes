package net.pieroxy.mom.diagnostics;

import java.util.List;

/**
 * The DNS queries {@link MxCheck} needs — MX records with their priority, and whether a name is
 * an alias — so it can be tested with in-memory answers. Every method returns an empty list for a
 * name or type that doesn't exist; {@link DnsLookupException} is for actual failures (timeout,
 * SERVFAIL…).
 */
public interface MxDnsResolver {
  record Mx(int priority, String host) {}

  /** The domain's MX records; {@code host} without its trailing dot ("" for a null MX, RFC 7505). */
  List<Mx> lookupMx(String domain) throws DnsLookupException;

  /** The name's CNAME target, if it's an alias (without its trailing dot). */
  List<String> lookupCname(String name) throws DnsLookupException;

  List<String> lookupA(String name) throws DnsLookupException;

  List<String> lookupAaaa(String name) throws DnsLookupException;

  class DnsLookupException extends Exception {
    public DnsLookupException(String message) {
      super(message);
    }
  }
}
