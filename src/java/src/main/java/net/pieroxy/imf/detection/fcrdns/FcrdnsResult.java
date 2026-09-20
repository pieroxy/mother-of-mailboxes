package net.pieroxy.imf.detection.fcrdns;

/**
 * Result of an FCrDNS (Forward-Confirmed reverse DNS) check on a connecting IP.
 * Unlike SPF/DKIM/DMARC, there is no dedicated authentication RFC defining this vocabulary —
 * these four values are an IMF-internal convention, not a standard.
 */
public enum FcrdnsResult {
  /** The IP has a PTR record, and that PTR is confirmed (an A/AAAA lookup of the hostname resolves back to the IP). */
  PASS,
  /** The IP has one or more PTR records, but none of them are confirmed. */
  FAIL,
  /** The IP has no PTR record at all. */
  NONE,
  /** A DNS resolution failed temporarily (timeout, SERVFAIL...). */
  TEMPERROR;

  public String getCode() {
    return name().toLowerCase();
  }
}
