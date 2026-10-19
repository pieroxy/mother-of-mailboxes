package net.pieroxy.mom.diagnostics;

import net.pieroxy.mom.api.metadata.TypeScriptNonConstEnum;
import net.pieroxy.mom.api.metadata.TypeScriptType;

/**
 * Every check of an account's delivery settings, in the order the webapp runs and shows them.
 * {@code @TypeScriptNonConstEnum} so the webapp can list them with {@code Object.values(...)}.
 */
@TypeScriptType
@TypeScriptNonConstEnum
public enum DeliveryCheck {
  SPF,
  DKIM,
  DMARC,
}
