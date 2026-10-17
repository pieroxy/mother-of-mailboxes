package net.pieroxy.mom.detection.spf;

/**
 * A temporary DNS resolution failure (timeout, SERVFAIL...), distinct from a "no record"
 * answer (which is itself a normal outcome: an empty list, not an exception).
 * Translates to {@link SpfResult#TEMPERROR} in {@link SpfEvaluator}.
 */
public class SpfDnsException extends Exception {
  public SpfDnsException(String message) {
    super(message);
  }

  public SpfDnsException(String message, Throwable cause) {
    super(message, cause);
  }
}
