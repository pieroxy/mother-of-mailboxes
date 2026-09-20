package net.pieroxy.imf.dmarc;

import net.pieroxy.imf.detection.dmarc.DmarcDnsException;
import net.pieroxy.imf.detection.dmarc.DmarcDnsResolver;
import net.pieroxy.imf.detection.dmarc.DmarcEvaluator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** In-memory DNS resolver, for testing {@link DmarcEvaluator} without a network. */
public class FakeDmarcDnsResolver implements DmarcDnsResolver {
  private final Map<String, List<String>> records = new HashMap<>();
  private final Set<String> failing = new java.util.HashSet<>();

  public FakeDmarcDnsResolver withTxt(String name, String... records) {
    this.records.put(name.toLowerCase(), List.of(records));
    return this;
  }

  public FakeDmarcDnsResolver withFailure(String name) {
    failing.add(name.toLowerCase());
    return this;
  }

  @Override
  public List<String> lookupTxt(String name) throws DmarcDnsException {
    if (failing.contains(name.toLowerCase())) {
      throw new DmarcDnsException("Simulated DNS failure for " + name);
    }
    return records.getOrDefault(name.toLowerCase(), List.of());
  }
}
