package net.pieroxy.mom.diagnostics;

import net.pieroxy.mom.spf.FakeSpfDnsResolver;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SpfCheckTest {
  private static DeliveryCheckResultDto run(FakeSpfDnsResolver dns, String smtpHost) {
    return new SpfCheck(dns).run("example.org", smtpHost);
  }

  private static boolean hasFinding(DeliveryCheckResultDto r, CheckStatus status, String fragment) {
    return r.getFindings().stream().anyMatch(f -> f.getStatus() == status && f.getMessage().contains(fragment));
  }

  private static String dump(DeliveryCheckResultDto r) {
    StringBuilder sb = new StringBuilder(r.getStatus() + ": " + r.getSummary());
    r.getFindings().forEach(f -> sb.append("\n  ").append(f.getStatus()).append(" ").append(f.getMessage()));
    return sb.toString();
  }

  @Test
  public void validRecordAuthorizingTheSmtpServer() {
    FakeSpfDnsResolver dns = new FakeSpfDnsResolver()
        .withTxt("example.org", "google-site-verification=xyz", "v=spf1 a:smtp.example.org include:_spf.provider.net -all")
        .withTxt("_spf.provider.net", "v=spf1 ip4:198.51.100.0/24 ~all")
        .withA("smtp.example.org", "203.0.113.5");
    DeliveryCheckResultDto r = run(dns, "smtp.example.org");
    assertEquals(dump(r), CheckStatus.OK, r.getStatus());
    assertEquals("SPF is correctly set up for example.org.", r.getSummary());
    assertTrue(dump(r), hasFinding(r, CheckStatus.OK, "2 DNS lookups"));
    assertTrue(dump(r), hasFinding(r, CheckStatus.OK, "ends with -all"));
    assertTrue(dump(r), hasFinding(r, CheckStatus.OK, "smtp.example.org (203.0.113.5) is allowed"));
  }

  @Test
  public void noRecord() {
    DeliveryCheckResultDto r = run(new FakeSpfDnsResolver().withTxt("example.org", "something else"), null);
    assertEquals(CheckStatus.ERROR, r.getStatus());
    assertTrue(r.getSummary(), r.getSummary().startsWith("No SPF record found for example.org"));
  }

  @Test
  public void multipleRecords() {
    DeliveryCheckResultDto r = run(new FakeSpfDnsResolver().withTxt("example.org", "v=spf1 -all", "v=spf1 mx -all"), null);
    assertEquals(CheckStatus.ERROR, r.getStatus());
    assertTrue(r.getSummary(), r.getSummary().startsWith("2 SPF records found"));
  }

  @Test
  public void dnsFailureIsAWarning() {
    DeliveryCheckResultDto r = run(new FakeSpfDnsResolver().withFailure("example.org"), null);
    assertEquals(CheckStatus.WARNING, r.getStatus());
  }

  @Test
  public void plusAllIsAnError() {
    DeliveryCheckResultDto r = run(new FakeSpfDnsResolver().withTxt("example.org", "v=spf1 mx +all"), null);
    assertEquals(dump(r), CheckStatus.ERROR, r.getStatus());
    assertTrue(dump(r), hasFinding(r, CheckStatus.ERROR, "any server in the world"));
  }

  @Test
  public void neutralOrMissingAllIsAWarning() {
    assertEquals(CheckStatus.WARNING, run(new FakeSpfDnsResolver().withTxt("example.org", "v=spf1 mx ?all"), null).getStatus());
    DeliveryCheckResultDto r = run(new FakeSpfDnsResolver().withTxt("example.org", "v=spf1 mx"), null);
    assertTrue(dump(r), hasFinding(r, CheckStatus.WARNING, "doesn't end with \"all\""));
  }

  @Test
  public void allComesFromTheRedirectTarget() {
    FakeSpfDnsResolver dns = new FakeSpfDnsResolver()
        .withTxt("example.org", "v=spf1 redirect=_spf.example.org")
        .withTxt("_spf.example.org", "v=spf1 ip4:192.0.2.1 -all");
    DeliveryCheckResultDto r = run(dns, null);
    assertEquals(dump(r), CheckStatus.OK, r.getStatus());
    assertTrue(dump(r), hasFinding(r, CheckStatus.OK, "1 DNS lookup needed"));
    assertTrue(dump(r), hasFinding(r, CheckStatus.INFO, "No SMTP server configured"));
  }

  @Test
  public void countsLookupsThroughIncludes() {
    FakeSpfDnsResolver dns = new FakeSpfDnsResolver()
        .withTxt("example.org", "v=spf1 include:a.net include:b.net mx a -all")
        .withTxt("a.net", "v=spf1 include:c.net a mx -all")
        .withTxt("b.net", "v=spf1 a mx exists:x.net -all")
        .withTxt("c.net", "v=spf1 a ip4:192.0.2.0/24 -all");
    DeliveryCheckResultDto r = run(dns, null);
    // example.org: 2 includes + mx + a = 4; a.net: include + a + mx = 3; b.net: 3; c.net: 1. Total 11.
    assertEquals(dump(r), CheckStatus.ERROR, r.getStatus());
    assertTrue(dump(r), hasFinding(r, CheckStatus.ERROR, "11 DNS lookups"));
  }

  @Test
  public void reportsBrokenIncludesLoopsPtrAndBadTerms() {
    FakeSpfDnsResolver dns = new FakeSpfDnsResolver()
        .withTxt("example.org", "v=spf1 ptr include:nospf.net include:loop.net foo:bar -all")
        .withTxt("loop.net", "v=spf1 include:example.org -all");
    DeliveryCheckResultDto r = run(dns, null);
    assertEquals(dump(r), CheckStatus.ERROR, r.getStatus());
    assertTrue(dump(r), hasFinding(r, CheckStatus.WARNING, "\"ptr\""));
    assertTrue(dump(r), hasFinding(r, CheckStatus.ERROR, "include:nospf.net\" in example.org's record points to a domain with no SPF records"));
    assertTrue(dump(r), hasFinding(r, CheckStatus.ERROR, "loops back"));
    assertTrue(dump(r), hasFinding(r, CheckStatus.ERROR, "Unrecognized term \"foo:bar\""));
  }

  @Test
  public void smtpServerNotAuthorizedIsAWarning() {
    FakeSpfDnsResolver dns = new FakeSpfDnsResolver()
        .withTxt("example.org", "v=spf1 ip4:192.0.2.1 -all")
        .withA("smtp.provider.net", "198.51.100.7")
        .withAaaa("smtp.provider.net", "2001:db8::7");
    DeliveryCheckResultDto r = run(dns, "smtp.provider.net");
    assertEquals(dump(r), CheckStatus.WARNING, r.getStatus());
    assertTrue(dump(r), hasFinding(r, CheckStatus.WARNING, "smtp.provider.net (198.51.100.7) is not allowed"));
    assertTrue(dump(r), hasFinding(r, CheckStatus.WARNING, "smtp.provider.net (2001:db8::7) is not allowed"));
  }

  @Test
  public void domainOfAddresses() {
    assertEquals("example.org", DeliveryChecks.domainOf("me@Example.ORG"));
    assertEquals("example.org", DeliveryChecks.domainOf("Me <me@example.org>"));
    assertEquals(null, DeliveryChecks.domainOf("nobody"));
    assertEquals(null, DeliveryChecks.domainOf("me@"));
    assertEquals(null, DeliveryChecks.domainOf(null));
  }
}
