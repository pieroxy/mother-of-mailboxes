package net.pieroxy.imf.fcrdns;

import net.pieroxy.imf.detection.fcrdns.FcrdnsDnsException;
import net.pieroxy.imf.detection.fcrdns.FcrdnsDnsResolver;
import net.pieroxy.imf.detection.fcrdns.FcrdnsEvaluator;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** In-memory DNS resolver, for testing {@link FcrdnsEvaluator} without a network. */
public class FakeFcrdnsDnsResolver implements FcrdnsDnsResolver {
  private final Map<String, List<String>> ptr = new HashMap<>();
  private final Map<String, List<String>> a = new HashMap<>();
  private final Map<String, List<String>> aaaa = new HashMap<>();
  private final Set<String> failing = new HashSet<>();

  public FakeFcrdnsDnsResolver withPtr(String ip, String... hostnames) {
    ptr.put(ip.toLowerCase(), List.of(hostnames));
    return this;
  }

  public FakeFcrdnsDnsResolver withA(String hostname, String... ips) {
    a.put(hostname.toLowerCase(), List.of(ips));
    return this;
  }

  public FakeFcrdnsDnsResolver withAaaa(String hostname, String... ips) {
    aaaa.put(hostname.toLowerCase(), List.of(ips));
    return this;
  }

  public FakeFcrdnsDnsResolver withFailure(String nameOrIp) {
    failing.add(nameOrIp.toLowerCase());
    return this;
  }

  @Override
  public List<String> lookupPtr(String ip) throws FcrdnsDnsException {
    checkFailure(ip);
    return ptr.getOrDefault(ip.toLowerCase(), List.of());
  }

  @Override
  public List<String> lookupA(String hostname) throws FcrdnsDnsException {
    checkFailure(hostname);
    return a.getOrDefault(hostname.toLowerCase(), List.of());
  }

  @Override
  public List<String> lookupAaaa(String hostname) throws FcrdnsDnsException {
    checkFailure(hostname);
    return aaaa.getOrDefault(hostname.toLowerCase(), List.of());
  }

  private void checkFailure(String nameOrIp) throws FcrdnsDnsException {
    if (failing.contains(nameOrIp.toLowerCase())) {
      throw new FcrdnsDnsException("Simulated DNS failure for " + nameOrIp);
    }
  }
}
