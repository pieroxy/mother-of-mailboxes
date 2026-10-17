package net.pieroxy.mom.detection.spf;

import net.pieroxy.mom.api.metadata.TypeScriptNonConstEnum;
import net.pieroxy.mom.api.metadata.TypeScriptType;

/**
 * Result of an SPF evaluation (RFC 7208 §2.6). The constant's lowercase name
 * ({@link #getCode()}) is what gets compared against the configured key on a matcher, exactly
 * like the values read from an {@code Authentication-Results} header (spf=pass, spf=fail...) —
 * that comparison is case-insensitive (see {@code SpfResultMatcher}), so the webapp's
 * SPF_RESULT_EQUALS dropdown (RuleEditPage) can offer this enum's own (uppercase) constant names
 * directly as values, with no need to convert to {@link #getCode()} first. {@code @TypeScriptNonConstEnum}
 * so the webapp can populate that dropdown via {@code Object.values(SpfResult)} instead of
 * hand-listing every constant.
 */
@TypeScriptType
@TypeScriptNonConstEnum
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
