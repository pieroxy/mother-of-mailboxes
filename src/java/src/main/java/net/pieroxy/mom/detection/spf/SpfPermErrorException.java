package net.pieroxy.mom.detection.spf;

/**
 * A permanent SPF evaluation error: malformed record, unknown mechanism, missing value,
 * exceeded DNS lookup budget (RFC 7208 §4.6.4), or an "include"/"redirect" pointing to a domain
 * without an SPF record. Distinct from {@link SpfDnsException} (temporary network failure):
 * here, retrying later wouldn't change anything until the SPF record itself is fixed.
 * Translates to {@link SpfResult#PERMERROR} in {@link SpfEvaluator}.
 */
class SpfPermErrorException extends Exception {
  SpfPermErrorException(String message) {
    super(message);
  }
}
