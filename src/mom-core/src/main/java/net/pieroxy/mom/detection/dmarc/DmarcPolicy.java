package net.pieroxy.mom.detection.dmarc;

import net.pieroxy.mom.api.metadata.TypeScriptNonConstEnum;
import net.pieroxy.mom.api.metadata.TypeScriptType;

/**
 * Effective DMARC policy for a given message: the exact domain's own (tag {@code p=}) if it
 * publishes its own record, or its organizational domain's for subdomains (tag {@code sp=},
 * falling back to {@code p=} if absent — RFC 7489 §6.3).
 * <p>
 * {@link #UNPUBLISHED} is deliberately distinct from {@link #NONE}: {@code p=none} means "the
 * domain has DMARC and explicitly chose to only monitor," while {@code UNPUBLISHED} means "this
 * domain has no DMARC at all" — two very different situations (the absence of DMARC is the norm
 * for most small/personal domains and isn't inherently suspicious, unlike {@code p=none}, which
 * is an active choice). Exposed to the webapp so the DMARC_POLICY_EQUALS matcher's value picker
 * (RuleEditPage) can offer these directly — the comparison against a configured key is
 * case-insensitive, so there's no need to go through {@link #getCode()} first.
 * {@code @TypeScriptNonConstEnum} so the webapp can populate that dropdown via
 * {@code Object.values(DmarcPolicy)} instead of hand-listing every constant.
 */
@TypeScriptType
@TypeScriptNonConstEnum
public enum DmarcPolicy {
  NONE,
  QUARANTINE,
  REJECT,
  UNPUBLISHED,
  PERMERROR,
  TEMPERROR;

  public String getCode() {
    return name().toLowerCase();
  }
}
