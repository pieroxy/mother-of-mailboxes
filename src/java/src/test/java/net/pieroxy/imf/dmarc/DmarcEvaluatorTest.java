package net.pieroxy.imf.dmarc;

import net.pieroxy.imf.detection.dmarc.DmarcEvaluator;
import net.pieroxy.imf.detection.dmarc.DmarcPolicy;
import net.pieroxy.imf.detection.dmarc.DmarcResult;
import org.junit.Test;

import java.util.List;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;

public class DmarcEvaluatorTest {

  @Test
  public void passesViaAlignedSpf() {
    FakeDmarcDnsResolver resolver = new FakeDmarcDnsResolver()
            .withTxt("_dmarc.example.com", "v=DMARC1; p=reject");
    DmarcEvaluator evaluator = new DmarcEvaluator(resolver);

    assertEquals(DmarcResult.PASS, evaluator.evaluate("example.com", true, "example.com", List.of()));
  }

  @Test
  public void passesViaAlignedDkim() {
    FakeDmarcDnsResolver resolver = new FakeDmarcDnsResolver()
            .withTxt("_dmarc.example.com", "v=DMARC1; p=reject");
    DmarcEvaluator evaluator = new DmarcEvaluator(resolver);

    assertEquals(DmarcResult.PASS, evaluator.evaluate("example.com", false, null, List.of("example.com")));
  }

  @Test
  public void failsWhenNeitherAligned() {
    FakeDmarcDnsResolver resolver = new FakeDmarcDnsResolver()
            .withTxt("_dmarc.example.com", "v=DMARC1; p=reject");
    DmarcEvaluator evaluator = new DmarcEvaluator(resolver);

    assertEquals(DmarcResult.FAIL, evaluator.evaluate("example.com", true, "other.com", List.of("another.com")));
  }

  @Test
  public void failsWhenSpfPassedButDomainNotAligned() {
    FakeDmarcDnsResolver resolver = new FakeDmarcDnsResolver()
            .withTxt("_dmarc.example.com", "v=DMARC1; p=reject");
    DmarcEvaluator evaluator = new DmarcEvaluator(resolver);

    // SPF passed, but for a domain unrelated to the displayed From: not aligned.
    assertEquals(DmarcResult.FAIL, evaluator.evaluate("example.com", true, "totally-unrelated.net", List.of()));
  }

  @Test
  public void relaxedAlignmentMatchesOrganizationalDomain() {
    FakeDmarcDnsResolver resolver = new FakeDmarcDnsResolver()
            .withTxt("_dmarc.news.example.com", "v=DMARC1; p=reject"); // aspf=r by default
    DmarcEvaluator evaluator = new DmarcEvaluator(resolver);

    // spfDomain has a different subdomain, but the same organizational domain.
    assertEquals(DmarcResult.PASS, evaluator.evaluate("news.example.com", true, "example.com", List.of()));
  }

  @Test
  public void strictAlignmentRequiresExactDomain() {
    FakeDmarcDnsResolver resolver = new FakeDmarcDnsResolver()
            .withTxt("_dmarc.news.example.com", "v=DMARC1; p=reject; aspf=s");
    DmarcEvaluator evaluator = new DmarcEvaluator(resolver);

    assertEquals(DmarcResult.FAIL, evaluator.evaluate("news.example.com", true, "example.com", List.of()));
  }

  @Test
  public void noRecordAnywhereYieldsNone() {
    DmarcEvaluator evaluator = new DmarcEvaluator(new FakeDmarcDnsResolver());

    assertEquals(DmarcResult.NONE, evaluator.evaluate("example.com", true, "example.com", List.of()));
  }

  @Test
  public void fallsBackToOrganizationalDomainRecord() {
    FakeDmarcDnsResolver resolver = new FakeDmarcDnsResolver()
            .withTxt("_dmarc.example.com", "v=DMARC1; p=reject"); // nothing at _dmarc.sub.example.com
    DmarcEvaluator evaluator = new DmarcEvaluator(resolver);

    assertEquals(DmarcResult.PASS, evaluator.evaluate("sub.example.com", true, "example.com", List.of()));
  }

  @Test
  public void missingPolicyTagIsPermError() {
    FakeDmarcDnsResolver resolver = new FakeDmarcDnsResolver()
            .withTxt("_dmarc.example.com", "v=DMARC1; adkim=s");
    DmarcEvaluator evaluator = new DmarcEvaluator(resolver);

    assertEquals(DmarcResult.PERMERROR, evaluator.evaluate("example.com", true, "example.com", List.of()));
  }

  @Test
  public void multipleRecordsIsPermError() {
    FakeDmarcDnsResolver resolver = new FakeDmarcDnsResolver()
            .withTxt("_dmarc.example.com", "v=DMARC1; p=none", "v=DMARC1; p=reject");
    DmarcEvaluator evaluator = new DmarcEvaluator(resolver);

    assertEquals(DmarcResult.PERMERROR, evaluator.evaluate("example.com", true, "example.com", List.of()));
  }

  @Test
  public void dnsFailureIsTempError() {
    FakeDmarcDnsResolver resolver = new FakeDmarcDnsResolver().withFailure("_dmarc.example.com");
    DmarcEvaluator evaluator = new DmarcEvaluator(resolver);

    assertEquals(DmarcResult.TEMPERROR, evaluator.evaluate("example.com", true, "example.com", List.of()));
  }

  @Test
  public void blankFromDomainYieldsNone() {
    DmarcEvaluator evaluator = new DmarcEvaluator(new FakeDmarcDnsResolver());

    assertEquals(DmarcResult.NONE, evaluator.evaluate("", true, "example.com", List.of()));
  }

  @Test
  public void resolvesThePublishedPolicy() {
    FakeDmarcDnsResolver resolver = new FakeDmarcDnsResolver()
            .withTxt("_dmarc.example.com", "v=DMARC1; p=reject");
    DmarcEvaluator evaluator = new DmarcEvaluator(resolver);

    assertEquals(DmarcPolicy.REJECT, evaluator.evaluateDetailed("example.com", true, "example.com", List.of(), Logger.getLogger("test")).policy());
  }

  @Test
  public void noRecordAtAllYieldsUnpublishedPolicyNotNone() {
    DmarcEvaluator evaluator = new DmarcEvaluator(new FakeDmarcDnsResolver());

    // "unpublished" (no DMARC at all) is deliberately distinct from "none" (p=none, an active
    // choice by the domain): don't conflate them.
    assertEquals(DmarcPolicy.UNPUBLISHED, evaluator.evaluateDetailed("example.com", true, "example.com", List.of(), Logger.getLogger("test")).policy());
  }

  @Test
  public void explicitPNoneIsDistinctFromUnpublished() {
    FakeDmarcDnsResolver resolver = new FakeDmarcDnsResolver()
            .withTxt("_dmarc.example.com", "v=DMARC1; p=none");
    DmarcEvaluator evaluator = new DmarcEvaluator(resolver);

    assertEquals(DmarcPolicy.NONE, evaluator.evaluateDetailed("example.com", true, "example.com", List.of(), Logger.getLogger("test")).policy());
  }

  @Test
  public void subdomainWithoutItsOwnRecordUsesOrganizationalSpNotP() {
    FakeDmarcDnsResolver resolver = new FakeDmarcDnsResolver()
            .withTxt("_dmarc.example.com", "v=DMARC1; p=reject; sp=quarantine");
    DmarcEvaluator evaluator = new DmarcEvaluator(resolver);

    assertEquals(DmarcPolicy.QUARANTINE, evaluator.evaluateDetailed("news.example.com", true, "example.com", List.of(), Logger.getLogger("test")).policy());
  }

  @Test
  public void subdomainFallsBackToPWhenSpIsAbsent() {
    FakeDmarcDnsResolver resolver = new FakeDmarcDnsResolver()
            .withTxt("_dmarc.example.com", "v=DMARC1; p=reject"); // no sp=
    DmarcEvaluator evaluator = new DmarcEvaluator(resolver);

    assertEquals(DmarcPolicy.REJECT, evaluator.evaluateDetailed("news.example.com", true, "example.com", List.of(), Logger.getLogger("test")).policy());
  }

  @Test
  public void invalidPolicyValueIsPermError() {
    FakeDmarcDnsResolver resolver = new FakeDmarcDnsResolver()
            .withTxt("_dmarc.example.com", "v=DMARC1; p=bogus");
    DmarcEvaluator evaluator = new DmarcEvaluator(resolver);

    assertEquals(DmarcResult.PERMERROR, evaluator.evaluate("example.com", true, "example.com", List.of()));
  }

  @Test
  public void invalidSubdomainPolicyValueIsPermError() {
    FakeDmarcDnsResolver resolver = new FakeDmarcDnsResolver()
            .withTxt("_dmarc.example.com", "v=DMARC1; p=reject; sp=bogus");
    DmarcEvaluator evaluator = new DmarcEvaluator(resolver);

    assertEquals(DmarcResult.PERMERROR, evaluator.evaluate("example.com", true, "example.com", List.of()));
  }
}
