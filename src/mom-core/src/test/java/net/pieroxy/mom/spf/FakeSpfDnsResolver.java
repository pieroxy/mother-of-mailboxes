package net.pieroxy.mom.spf;

import net.pieroxy.mom.detection.spf.SpfDnsException;
import net.pieroxy.mom.detection.spf.SpfDnsResolver;
import net.pieroxy.mom.detection.spf.SpfEvaluator;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** In-memory DNS resolver, for testing {@link SpfEvaluator} without the network. */
public class FakeSpfDnsResolver implements SpfDnsResolver {
  private final Map<String, List<String>> txt = new HashMap<>();
  private final Map<String, List<String>> a = new HashMap<>();
  private final Map<String, List<String>> aaaa = new HashMap<>();
  private final Map<String, List<String>> mx = new HashMap<>();
  private final Set<String> failing = new HashSet<>();

  public FakeSpfDnsResolver withTxt(String domain, String... records) {
    txt.put(domain.toLowerCase(), List.of(records));
    return this;
  }

  public FakeSpfDnsResolver withA(String domain, String... ips) {
    a.put(domain.toLowerCase(), List.of(ips));
    return this;
  }

  public FakeSpfDnsResolver withAaaa(String domain, String... ips) {
    aaaa.put(domain.toLowerCase(), List.of(ips));
    return this;
  }

  public FakeSpfDnsResolver withMx(String domain, String... hosts) {
    mx.put(domain.toLowerCase(), List.of(hosts));
    return this;
  }

  public FakeSpfDnsResolver withFailure(String domain) {
    failing.add(domain.toLowerCase());
    return this;
  }

  @Override
  public List<String> lookupTxt(String domain) throws SpfDnsException {
    checkFailure(domain);
    return txt.getOrDefault(domain.toLowerCase(), List.of());
  }

  @Override
  public List<String> lookupA(String domain) throws SpfDnsException {
    checkFailure(domain);
    return a.getOrDefault(domain.toLowerCase(), List.of());
  }

  @Override
  public List<String> lookupAaaa(String domain) throws SpfDnsException {
    checkFailure(domain);
    return aaaa.getOrDefault(domain.toLowerCase(), List.of());
  }

  @Override
  public List<String> lookupMx(String domain) throws SpfDnsException {
    checkFailure(domain);
    return mx.getOrDefault(domain.toLowerCase(), List.of());
  }

  private void checkFailure(String domain) throws SpfDnsException {
    if (failing.contains(domain.toLowerCase())) {
      throw new SpfDnsException("Simulated DNS failure for " + domain);
    }
  }
}
