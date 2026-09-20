package net.pieroxy.imf.detection.spf;

/**
 * Result of an SPF evaluation (RFC 7208 §2.6). The constant's lowercase name
 * ({@link #getCode()}) is what gets compared against the configured key on a matcher, exactly
 * like the values read from an {@code Authentication-Results} header (spf=pass, spf=fail...).
 */
public enum SpfResult {
  PASS,
  FAIL,
  SOFTFAIL,
  NEUTRAL,
  NONE,
  PERMERROR,
  TEMPERROR;

  public String getCode() {
    return name().toLowerCase();
  }
}
