package net.pieroxy.mom.detection.dmarc;

/**
 * Temporary DNS resolution failure (timeout, SERVFAIL...) while looking up a DMARC record.
 * Translates to {@link DmarcResult#TEMPERROR} in {@link DmarcEvaluator}.
 */
public class DmarcDnsException extends Exception {
  public DmarcDnsException(String message) {
    super(message);
  }

  public DmarcDnsException(String message, Throwable cause) {
    super(message, cause);
  }
}
