package net.pieroxy.imf.detection.reputation;

import java.util.Locale;
import java.util.Set;

/** Exact match, case-insensitive — no fallback to the parent domain. */
final class DomainReputationList implements ReputationList {
  private final Set<String> domains;

  DomainReputationList(Set<String> domains) {
    this.domains = domains;
  }

  @Override
  public boolean contains(String value) {
    return value != null && domains.contains(value.toLowerCase(Locale.ROOT));
  }

  int size() {
    return domains.size();
  }
}
