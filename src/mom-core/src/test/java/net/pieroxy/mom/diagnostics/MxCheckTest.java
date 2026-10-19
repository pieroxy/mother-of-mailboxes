package net.pieroxy.mom.diagnostics;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MxCheckTest {
  /** In-memory DNS. */
  private static class FakeDns implements MxDnsResolver {
    final Map<String, List<Mx>> mx = new HashMap<>();
    final Map<String, List<String>> cname = new HashMap<>(), a = new HashMap<>(), aaaa = new HashMap<>();
    final Set<String> failing = new HashSet<>();

    FakeDns mx(String domain, int priority, String host) {
      mx.computeIfAbsent(domain, k -> new ArrayList<>()).add(new Mx(priority, host));
      return this;
    }

    FakeDns a(String name, String... ips) {
      a.put(name, List.of(ips));
      return this;
    }

    FakeDns aaaa(String name, String... ips) {
      aaaa.put(name, List.of(ips));
      return this;
    }

    FakeDns cname(String name, String target) {
      cname.put(name, List.of(target));
      return this;
    }

    private void fail(String name) throws DnsLookupException {
      if (failing.contains(name)) throw new DnsLookupException("timeout");
    }

    public List<Mx> lookupMx(String domain) throws DnsLookupException {
      fail(domain);
      return mx.getOrDefault(domain, List.of());
    }

    public List<String> lookupCname(String name) throws DnsLookupException {
      fail(name);
      return cname.getOrDefault(name, List.of());
    }

    public List<String> lookupA(String name) throws DnsLookupException {
      fail(name);
      return a.getOrDefault(name, List.of());
    }

    public List<String> lookupAaaa(String name) throws DnsLookupException {
      fail(name);
      return aaaa.getOrDefault(name, List.of());
    }
  }

  private static DeliveryCheckResultDto run(FakeDns dns) {
    return new MxCheck(dns).run("example.org");
  }

  private static boolean hasFinding(DeliveryCheckResultDto r, CheckStatus status, String fragment) {
    return r.getFindings().stream().anyMatch(f -> f.getStatus() == status && f.getMessage().contains(fragment));
  }

  private static String dump(DeliveryCheckResultDto r) {
    StringBuilder sb = new StringBuilder(r.getStatus() + ": " + r.getSummary());
    r.getFacts().forEach(f -> sb.append("\n  ").append(f.getLabel()).append(": ").append(f.getValue()));
    r.getFindings().forEach(f -> sb.append("\n  ").append(f.getStatus()).append(" ").append(f.getMessage()));
    return sb.toString();
  }

  @Test
  public void validMxRecords() {
    DeliveryCheckResultDto r = run(new FakeDns()
        .mx("example.org", 20, "mx2.example.org").mx("example.org", 10, "mx1.example.org")
        .a("mx1.example.org", "203.0.113.25").aaaa("mx1.example.org", "2001:db8::25").a("mx2.example.org", "198.51.100.25"));
    assertEquals(dump(r), CheckStatus.OK, r.getStatus());
    assertFalse(r.isPartial());
    assertEquals("example.org has valid MX records: it can receive replies and bounces.", r.getSummary());
    assertTrue(dump(r), r.getFacts().stream().anyMatch(f -> f.getValue().equals("10 mx1.example.org, 20 mx2.example.org")));
    assertTrue(dump(r), hasFinding(r, CheckStatus.OK, "2 of its 2 MX records lead"));
  }

  @Test
  public void noMxButAnAddress() {
    DeliveryCheckResultDto r = run(new FakeDns().a("example.org", "203.0.113.25"));
    assertEquals(dump(r), CheckStatus.WARNING, r.getStatus());
    assertTrue(r.getSummary(), r.getSummary().startsWith("example.org has no MX record: servers fall back"));
  }

  @Test
  public void noMxNorAddress() {
    DeliveryCheckResultDto r = run(new FakeDns());
    assertEquals(dump(r), CheckStatus.ERROR, r.getStatus());
    assertTrue(r.getSummary(), r.getSummary().contains("it can't receive mail"));
  }

  @Test
  public void nullMx() {
    DeliveryCheckResultDto r = run(new FakeDns().mx("example.org", 0, ""));
    assertEquals(dump(r), CheckStatus.ERROR, r.getStatus());
    assertTrue(dump(r), hasFinding(r, CheckStatus.ERROR, "null MX"));
  }

  @Test
  public void brokenHosts() {
    DeliveryCheckResultDto r = run(new FakeDns()
        .mx("example.org", 10, "203.0.113.25")
        .mx("example.org", 20, "nowhere.example.org")
        .mx("example.org", 30, "lan.example.org").a("lan.example.org", "192.168.1.10"));
    assertEquals(dump(r), CheckStatus.ERROR, r.getStatus());
    assertTrue(dump(r), hasFinding(r, CheckStatus.ERROR, "is an IP address"));
    assertTrue(dump(r), hasFinding(r, CheckStatus.ERROR, "nowhere.example.org doesn't resolve"));
    assertTrue(dump(r), hasFinding(r, CheckStatus.ERROR, "only resolves to private or reserved addresses (192.168.1.10)"));
    assertTrue(dump(r), hasFinding(r, CheckStatus.ERROR, "None of example.org's MX records"));
  }

  @Test
  public void oneBrokenAmongWorkingOnes() {
    DeliveryCheckResultDto r = run(new FakeDns()
        .mx("example.org", 10, "mx1.example.org").a("mx1.example.org", "203.0.113.25")
        .mx("example.org", 20, "nowhere.example.org"));
    assertEquals(dump(r), CheckStatus.ERROR, r.getStatus());
    assertTrue(dump(r), hasFinding(r, CheckStatus.OK, "1 of its 2 MX records lead"));
  }

  @Test
  public void cnameIsAWarning() {
    DeliveryCheckResultDto r = run(new FakeDns()
        .mx("example.org", 10, "mail.example.org").cname("mail.example.org", "real.provider.net")
        .a("mail.example.org", "203.0.113.25"));
    assertEquals(dump(r), CheckStatus.WARNING, r.getStatus());
    assertTrue(dump(r), hasFinding(r, CheckStatus.WARNING, "is an alias (CNAME to real.provider.net)"));
  }

  @Test
  public void dnsFailure() {
    FakeDns dns = new FakeDns();
    dns.failing.add("example.org");
    assertEquals(CheckStatus.WARNING, run(dns).getStatus());
  }

  @Test
  public void nonPublicAddresses() {
    for (String ip : new String[]{"127.0.0.1", "10.1.2.3", "172.16.0.1", "192.168.0.1", "169.254.1.1", "100.64.0.1", "0.0.0.0",
        "::1", "fd00::1", "fe80::1"}) {
      assertTrue(ip, MxCheck.isNonPublic(ip));
    }
    for (String ip : new String[]{"203.0.113.25", "82.65.93.243", "2001:db8::25", "2a01:e0a::1"}) {
      assertFalse(ip, MxCheck.isNonPublic(ip));
    }
  }
}
