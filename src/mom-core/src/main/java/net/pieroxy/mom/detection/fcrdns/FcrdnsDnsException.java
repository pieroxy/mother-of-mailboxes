package net.pieroxy.mom.detection.fcrdns;

/**
 * A temporary DNS resolution failure (timeout, SERVFAIL...), whether from the PTR lookup or the
 * confirming forward (A/AAAA) lookup. Translates to {@link FcrdnsResult#TEMPERROR}.
 */
public class FcrdnsDnsException extends Exception {
  public FcrdnsDnsException(String message) {
    super(message);
  }

  public FcrdnsDnsException(String message, Throwable cause) {
    super(message, cause);
  }
}
