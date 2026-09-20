package net.pieroxy.imf.spf;

import net.pieroxy.imf.detection.spf.SpfEvaluator;
import net.pieroxy.imf.detection.spf.SpfResult;
import org.junit.Test;

import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;

public class SpfEvaluatorTest {

  @Test
  public void evaluateWithExplicitLoggerBehavesLikeDefault() {
    FakeSpfDnsResolver resolver = new FakeSpfDnsResolver()
            .withTxt("example.com", "v=spf1 ip4:203.0.113.0/24 -all");
    SpfEvaluator evaluator = new SpfEvaluator(resolver);

    assertEquals(SpfResult.PASS, evaluator.evaluate("203.0.113.42", "example.com", Logger.getLogger("test")));
  }

  @Test
  public void ip4MechanismPasses() {
    FakeSpfDnsResolver resolver = new FakeSpfDnsResolver()
            .withTxt("example.com", "v=spf1 ip4:203.0.113.0/24 -all");
    SpfEvaluator evaluator = new SpfEvaluator(resolver);

    assertEquals(SpfResult.PASS, evaluator.evaluate("203.0.113.42", "example.com"));
  }

  @Test
  public void ip4MechanismOutsideRangeFallsThroughToAll() {
    FakeSpfDnsResolver resolver = new FakeSpfDnsResolver()
            .withTxt("example.com", "v=spf1 ip4:203.0.113.0/24 -all");
    SpfEvaluator evaluator = new SpfEvaluator(resolver);

    assertEquals(SpfResult.FAIL, evaluator.evaluate("198.51.100.1", "example.com"));
  }

  @Test
  public void softfailQualifier() {
    FakeSpfDnsResolver resolver = new FakeSpfDnsResolver()
            .withTxt("example.com", "v=spf1 ip4:203.0.113.0/24 ~all");
    SpfEvaluator evaluator = new SpfEvaluator(resolver);

    assertEquals(SpfResult.SOFTFAIL, evaluator.evaluate("198.51.100.1", "example.com"));
  }

  @Test
  public void neutralQualifier() {
    FakeSpfDnsResolver resolver = new FakeSpfDnsResolver()
            .withTxt("example.com", "v=spf1 ip4:203.0.113.0/24 ?all");
    SpfEvaluator evaluator = new SpfEvaluator(resolver);

    assertEquals(SpfResult.NEUTRAL, evaluator.evaluate("198.51.100.1", "example.com"));
  }

  @Test
  public void noAllMechanismAndNoRedirectDefaultsToNeutral() {
    FakeSpfDnsResolver resolver = new FakeSpfDnsResolver()
            .withTxt("example.com", "v=spf1 ip4:203.0.113.0/24");
    SpfEvaluator evaluator = new SpfEvaluator(resolver);

    assertEquals(SpfResult.NEUTRAL, evaluator.evaluate("198.51.100.1", "example.com"));
  }

  @Test
  public void noSpfRecordAtAllReturnsNone() {
    FakeSpfDnsResolver resolver = new FakeSpfDnsResolver();
    SpfEvaluator evaluator = new SpfEvaluator(resolver);

    assertEquals(SpfResult.NONE, evaluator.evaluate("203.0.113.42", "example.com"));
  }

  @Test
  public void multipleSpfRecordsIsPermError() {
    FakeSpfDnsResolver resolver = new FakeSpfDnsResolver()
            .withTxt("example.com", "v=spf1 -all", "v=spf1 +all");
    SpfEvaluator evaluator = new SpfEvaluator(resolver);

    assertEquals(SpfResult.PERMERROR, evaluator.evaluate("203.0.113.42", "example.com"));
  }

  @Test
  public void unknownMechanismIsPermError() {
    FakeSpfDnsResolver resolver = new FakeSpfDnsResolver()
            .withTxt("example.com", "v=spf1 frobnicate:whatever -all");
    SpfEvaluator evaluator = new SpfEvaluator(resolver);

    assertEquals(SpfResult.PERMERROR, evaluator.evaluate("203.0.113.42", "example.com"));
  }

  @Test
  public void aMechanismResolvesDomainOfRecord() {
    FakeSpfDnsResolver resolver = new FakeSpfDnsResolver()
            .withTxt("example.com", "v=spf1 a -all")
            .withA("example.com", "203.0.113.5");
    SpfEvaluator evaluator = new SpfEvaluator(resolver);

    assertEquals(SpfResult.PASS, evaluator.evaluate("203.0.113.5", "example.com"));
    assertEquals(SpfResult.FAIL, evaluator.evaluate("203.0.113.6", "example.com"));
  }

  @Test
  public void aMechanismWithExplicitDomainAndCidr() {
    FakeSpfDnsResolver resolver = new FakeSpfDnsResolver()
            .withTxt("example.com", "v=spf1 a:mail.example.net/24 -all")
            .withA("mail.example.net", "203.0.113.5");
    SpfEvaluator evaluator = new SpfEvaluator(resolver);

    assertEquals(SpfResult.PASS, evaluator.evaluate("203.0.113.99", "example.com"));
  }

  @Test
  public void mxMechanismResolvesMxHostsThenTheirAddresses() {
    FakeSpfDnsResolver resolver = new FakeSpfDnsResolver()
            .withTxt("example.com", "v=spf1 mx -all")
            .withMx("example.com", "mail1.example.com", "mail2.example.com")
            .withA("mail1.example.com", "203.0.113.1")
            .withA("mail2.example.com", "203.0.113.2");
    SpfEvaluator evaluator = new SpfEvaluator(resolver);

    assertEquals(SpfResult.PASS, evaluator.evaluate("203.0.113.2", "example.com"));
    assertEquals(SpfResult.FAIL, evaluator.evaluate("203.0.113.3", "example.com"));
  }

  @Test
  public void ip6Mechanism() {
    FakeSpfDnsResolver resolver = new FakeSpfDnsResolver()
            .withTxt("example.com", "v=spf1 ip6:2001:db8::/32 -all");
    SpfEvaluator evaluator = new SpfEvaluator(resolver);

    assertEquals(SpfResult.PASS, evaluator.evaluate("2001:db8::1", "example.com"));
    assertEquals(SpfResult.FAIL, evaluator.evaluate("2001:db9::1", "example.com"));
  }

  @Test
  public void includeMechanismPassesThroughSubdomainPass() {
    FakeSpfDnsResolver resolver = new FakeSpfDnsResolver()
            .withTxt("example.com", "v=spf1 include:_spf.example.net -all")
            .withTxt("_spf.example.net", "v=spf1 ip4:203.0.113.0/24 -all");
    SpfEvaluator evaluator = new SpfEvaluator(resolver);

    assertEquals(SpfResult.PASS, evaluator.evaluate("203.0.113.10", "example.com"));
  }

  @Test
  public void includeMechanismNonPassFallsThroughToNextMechanism() {
    FakeSpfDnsResolver resolver = new FakeSpfDnsResolver()
            .withTxt("example.com", "v=spf1 include:_spf.example.net ip4:198.51.100.0/24 -all")
            .withTxt("_spf.example.net", "v=spf1 ip4:203.0.113.0/24 -all");
    SpfEvaluator evaluator = new SpfEvaluator(resolver);

    assertEquals(SpfResult.PASS, evaluator.evaluate("198.51.100.1", "example.com"));
  }

  @Test
  public void includeOfDomainWithNoSpfRecordIsPermError() {
    FakeSpfDnsResolver resolver = new FakeSpfDnsResolver()
            .withTxt("example.com", "v=spf1 include:_spf.example.net -all");
    SpfEvaluator evaluator = new SpfEvaluator(resolver);

    assertEquals(SpfResult.PERMERROR, evaluator.evaluate("203.0.113.10", "example.com"));
  }

  @Test
  public void redirectModifierIsFollowedWhenNothingElseMatches() {
    FakeSpfDnsResolver resolver = new FakeSpfDnsResolver()
            .withTxt("example.com", "v=spf1 redirect=_spf.example.net")
            .withTxt("_spf.example.net", "v=spf1 ip4:203.0.113.0/24 -all");
    SpfEvaluator evaluator = new SpfEvaluator(resolver);

    assertEquals(SpfResult.PASS, evaluator.evaluate("203.0.113.10", "example.com"));
    assertEquals(SpfResult.FAIL, evaluator.evaluate("198.51.100.1", "example.com"));
  }

  @Test
  public void redirectToDomainWithNoSpfRecordIsPermError() {
    FakeSpfDnsResolver resolver = new FakeSpfDnsResolver()
            .withTxt("example.com", "v=spf1 redirect=_spf.example.net");
    SpfEvaluator evaluator = new SpfEvaluator(resolver);

    assertEquals(SpfResult.PERMERROR, evaluator.evaluate("203.0.113.10", "example.com"));
  }

  @Test
  public void dnsFailureOnTopLevelDomainIsTempError() {
    FakeSpfDnsResolver resolver = new FakeSpfDnsResolver().withFailure("example.com");
    SpfEvaluator evaluator = new SpfEvaluator(resolver);

    assertEquals(SpfResult.TEMPERROR, evaluator.evaluate("203.0.113.10", "example.com"));
  }

  @Test
  public void tooManyIncludesIsPermError() {
    FakeSpfDnsResolver resolver = new FakeSpfDnsResolver();
    resolver.withTxt("example.com", "v=spf1 include:c1.example.com include:c2.example.com "
            + "include:c3.example.com include:c4.example.com include:c5.example.com "
            + "include:c6.example.com include:c7.example.com include:c8.example.com "
            + "include:c9.example.com include:c10.example.com include:c11.example.com -all");
    for (int i = 1; i <= 11; i++) {
      resolver.withTxt("c" + i + ".example.com", "v=spf1 -all");
    }
    SpfEvaluator evaluator = new SpfEvaluator(resolver);

    assertEquals(SpfResult.PERMERROR, evaluator.evaluate("203.0.113.10", "example.com"));
  }

  @Test
  public void macroInMechanismIsSkippedNotFatal() {
    FakeSpfDnsResolver resolver = new FakeSpfDnsResolver()
            .withTxt("example.com", "v=spf1 exists:%{i}.spf.example.net ip4:203.0.113.0/24 -all");
    SpfEvaluator evaluator = new SpfEvaluator(resolver);

    assertEquals(SpfResult.PASS, evaluator.evaluate("203.0.113.10", "example.com"));
  }

  @Test
  public void ptrMechanismNeverMatches() {
    FakeSpfDnsResolver resolver = new FakeSpfDnsResolver()
            .withTxt("example.com", "v=spf1 ptr -all");
    SpfEvaluator evaluator = new SpfEvaluator(resolver);

    assertEquals(SpfResult.FAIL, evaluator.evaluate("203.0.113.10", "example.com"));
  }

  @Test
  public void notALiteralIpReturnsNone() {
    FakeSpfDnsResolver resolver = new FakeSpfDnsResolver()
            .withTxt("example.com", "v=spf1 -all");
    SpfEvaluator evaluator = new SpfEvaluator(resolver);

    assertEquals(SpfResult.NONE, evaluator.evaluate("not-an-ip", "example.com"));
  }
}
