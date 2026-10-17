package net.pieroxy.mom.detection.dmarc;

import net.pieroxy.mom.api.metadata.TypeScriptNonConstEnum;
import net.pieroxy.mom.api.metadata.TypeScriptType;

/**
 * Result of a DMARC evaluation (RFC 7489 §11.2). Unlike SPF/DKIM, DMARC only has five possible
 * outcomes: there's no "softfail"/"neutral"/"policy" — DMARC passes or fails, purely based on
 * SPF/DKIM alignment (see {@link DmarcEvaluator}). Exposed to the webapp so the
 * DMARC_RESULT_EQUALS matcher's value picker (RuleEditPage) can offer these directly — the
 * comparison against a configured key is case-insensitive, so there's no need to go through
 * {@link #getCode()} first. {@code @TypeScriptNonConstEnum} so the webapp can populate that
 * dropdown via {@code Object.values(DmarcResult)} instead of hand-listing every constant.
 */
@TypeScriptType
@TypeScriptNonConstEnum
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
