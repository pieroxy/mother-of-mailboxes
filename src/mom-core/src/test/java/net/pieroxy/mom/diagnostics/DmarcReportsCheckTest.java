package net.pieroxy.mom.diagnostics;

import net.pieroxy.mom.detection.dmarc.FakeDmarcDnsResolver;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DmarcReportsCheckTest {
  /** example.org, reports.example and provider.net receive mail; nomail.example has nothing; nullmx.example refuses all mail. */
  private static final MxDnsResolver MX = new MxDnsResolver() {
    final Map<String, List<Mx>> mx = Map.of(
        "example.org", List.of(new Mx(10, "mx.example.org")),
        "reports.example", List.of(new Mx(10, "mx.reports.example")),
        "nullmx.example", List.of(new Mx(0, "")));

    public List<Mx> lookupMx(String domain) {
      return mx.getOrDefault(domain, List.of());
    }

    public List<String> lookupCname(String name) {
      return List.of();
    }

    public List<String> lookupA(String name) {
      return name.equals("provider.net") ? List.of("203.0.113.1") : List.of();
    }

    public List<String> lookupAaaa(String name) {
      return List.of();
    }
  };

  private static DeliveryCheckResultDto run(FakeDmarcDnsResolver dns, String fromDomain) {
    return new DmarcReportsCheck(dns, MX).run(fromDomain);
  }

  private static DeliveryCheckResultDto run(String record) {
    return run(new FakeDmarcDnsResolver().withTxt("_dmarc.example.org", record), "example.org");
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
  public void missingRuaSeverityFollowsThePolicy() {
    DeliveryCheckResultDto none = run("v=DMARC1; p=none");
    assertEquals(dump(none), CheckStatus.WARNING, none.getStatus());
    assertTrue(dump(none), hasFinding(none, CheckStatus.WARNING, "with p=none"));

    DeliveryCheckResultDto quarantine = run("v=DMARC1; p=quarantine");
    assertEquals(dump(quarantine), CheckStatus.INFO, quarantine.getStatus());
    assertEquals("No DMARC aggregate reports for example.org: you won't see what p=quarantine sends to spam.", quarantine.getSummary());

    DeliveryCheckResultDto reject = run("v=DMARC1; p=reject");
    assertEquals(dump(reject), CheckStatus.OK, reject.getStatus());
    assertEquals("No DMARC aggregate reports for example.org, but p=reject protects it without them.", reject.getSummary());
    assertTrue(dump(reject), hasFinding(reject, CheckStatus.INFO, "who tries to send mail as your domain"));
  }

  @Test
  public void reportsToTheDomainItself() {
    DeliveryCheckResultDto r = run("v=DMARC1; p=none; rua=mailto:dmarc@example.org!10m");
    assertEquals(dump(r), CheckStatus.OK, r.getStatus());
    assertEquals("DMARC aggregate reports are set up for example.org: they go to mailto:dmarc@example.org!10m.", r.getSummary());
    assertTrue(dump(r), hasFinding(r, CheckStatus.OK, "Reports go to dmarc@example.org."));
  }

  @Test
  public void subdomainDestinationNeedsNoAuthorization() {
    DeliveryCheckResultDto r = run(new FakeDmarcDnsResolver().withTxt("_dmarc.mail.example.org", "v=DMARC1; p=reject; rua=mailto:d@example.org"),
        "mail.example.org");
    assertEquals(dump(r), CheckStatus.OK, r.getStatus());
  }

  @Test
  public void externalDestinationMustBeAuthorized() {
    DeliveryCheckResultDto missing = run("v=DMARC1; p=reject; rua=mailto:x@reports.example");
    assertEquals(dump(missing), CheckStatus.ERROR, missing.getStatus());
    assertTrue(dump(missing), hasFinding(missing, CheckStatus.ERROR, "example.org._report._dmarc.reports.example"));

    DeliveryCheckResultDto authorized = run(new FakeDmarcDnsResolver()
        .withTxt("_dmarc.example.org", "v=DMARC1; p=reject; rua=mailto:x@reports.example")
        .withTxt("example.org._report._dmarc.reports.example", "v=DMARC1"), "example.org");
    assertEquals(dump(authorized), CheckStatus.OK, authorized.getStatus());
    assertTrue(dump(authorized), hasFinding(authorized, CheckStatus.OK, "reports.example agrees to receive them for example.org"));
  }

  @Test
  public void inheritedRecordIsJudgedForTheOrganizationalDomain() {
    // mail.example.org has no record: example.org's applies, and its own domain needs no authorization.
    DeliveryCheckResultDto r = run(new FakeDmarcDnsResolver().withTxt("_dmarc.example.org", "v=DMARC1; p=reject; rua=mailto:d@example.org"),
        "mail.example.org");
    assertEquals(dump(r), CheckStatus.OK, r.getStatus());
    assertTrue(dump(r), r.getFacts().stream().anyMatch(f -> f.getValue().contains("inherited from example.org")));
  }

  @Test
  public void undeliverableOrInvalidDestinations() {
    assertTrue(hasFinding(run("v=DMARC1; p=none; rua=mailto:x@nomail.example"), CheckStatus.ERROR, "no MX record nor address"));
    assertTrue(hasFinding(run("v=DMARC1; p=none; rua=mailto:x@nullmx.example"), CheckStatus.ERROR, "accepts no mail (null MX)"));
    assertTrue(hasFinding(run("v=DMARC1; p=none; rua=dmarc@example.org"), CheckStatus.ERROR, "isn't a valid mailto: address"));
    // An address without MX but with an A record can still receive.
    DeliveryCheckResultDto aOnly = run(new FakeDmarcDnsResolver()
        .withTxt("_dmarc.example.org", "v=DMARC1; p=none; rua=mailto:x@provider.net")
        .withTxt("example.org._report._dmarc.provider.net", "v=DMARC1"), "example.org");
    assertEquals(dump(aOnly), CheckStatus.OK, aOnly.getStatus());
  }

  @Test
  public void severalDestinationsRufAndRi() {
    DeliveryCheckResultDto r = run("v=DMARC1; p=quarantine; rua=mailto:a@example.org, mailto:b@example.org; ruf=mailto:f@example.org; ri=3600");
    assertEquals(dump(r), CheckStatus.OK, r.getStatus());
    assertTrue(dump(r), hasFinding(r, CheckStatus.OK, "Reports go to a@example.org."));
    assertTrue(dump(r), hasFinding(r, CheckStatus.OK, "Reports go to b@example.org."));
    assertTrue(dump(r), hasFinding(r, CheckStatus.INFO, "ruf= asks for per-message failure reports"));
    assertTrue(dump(r), hasFinding(r, CheckStatus.INFO, "ri=3600"));
  }

  @Test
  public void noValidRecordIsSkipped() {
    assertEquals(CheckStatus.SKIPPED, run(new FakeDmarcDnsResolver(), "example.org").getStatus());
    assertEquals(CheckStatus.SKIPPED, run("v=DMARC1; rua=mailto:d@example.org").getStatus());
  }
}
