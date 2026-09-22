package net.pieroxy.mom.detection.dkim;

/**
 * Result of a DKIM verification (RFC 6376), using RFC 8601 §2.7.1 vocabulary. The constant names
 * match {@code org.apache.james.jdkim.api.Result.Type} exactly, for a direct conversion via
 * {@link Enum#valueOf}.
 */
public enum DkimResult {
  NONE,
  PASS,
  FAIL,
  POLICY,
  NEUTRAL,
  TEMPERROR,
  PERMERROR;

  public String getCode() {
    return name().toLowerCase();
  }
}
