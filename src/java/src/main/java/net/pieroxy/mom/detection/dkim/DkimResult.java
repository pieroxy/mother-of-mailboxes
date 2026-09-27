package net.pieroxy.mom.detection.dkim;

import net.pieroxy.mom.api.metadata.TypeScriptNonConstEnum;
import net.pieroxy.mom.api.metadata.TypeScriptType;

/**
 * Result of a DKIM verification (RFC 6376), using RFC 8601 §2.7.1 vocabulary. The constant names
 * match {@code org.apache.james.jdkim.api.Result.Type} exactly, for a direct conversion via
 * {@link Enum#valueOf}. Exposed to the webapp so the DKIM_RESULT_EQUALS matcher's value picker
 * (RuleEditPage) can offer these directly — the comparison against a configured key is
 * case-insensitive, so there's no need to go through {@link #getCode()} first.
 * {@code @TypeScriptNonConstEnum} so the webapp can populate that dropdown via
 * {@code Object.values(DkimResult)} instead of hand-listing every constant.
 */
@TypeScriptType
@TypeScriptNonConstEnum
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
