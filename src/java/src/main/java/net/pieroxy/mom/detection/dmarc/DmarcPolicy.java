package net.pieroxy.mom.detection.dmarc;

/**
 * Effective DMARC policy for a given message: the exact domain's own (tag {@code p=}) if it
 * publishes its own record, or its organizational domain's for subdomains (tag {@code sp=},
 * falling back to {@code p=} if absent — RFC 7489 §6.3).
 * <p>
 * {@link #UNPUBLISHED} is deliberately distinct from {@link #NONE}: {@code p=none} means "the
 * domain has DMARC and explicitly chose to only monitor," while {@code UNPUBLISHED} means "this
 * domain has no DMARC at all" — two very different situations (the absence of DMARC is the norm
 * for most small/personal domains and isn't inherently suspicious, unlike {@code p=none}, which
 * is an active choice).
 */
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
