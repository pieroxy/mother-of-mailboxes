package net.pieroxy.mom.detection.dmarc;

/**
 * Result of a DMARC evaluation (RFC 7489 §11.2). Unlike SPF/DKIM, DMARC only has five possible
 * outcomes: there's no "softfail"/"neutral"/"policy" — DMARC passes or fails, purely based on
 * SPF/DKIM alignment (see {@link DmarcEvaluator}).
 */
public enum DmarcResult {
  NONE,
  PASS,
  FAIL,
  TEMPERROR,
  PERMERROR;

  public String getCode() {
    return name().toLowerCase();
  }
}
