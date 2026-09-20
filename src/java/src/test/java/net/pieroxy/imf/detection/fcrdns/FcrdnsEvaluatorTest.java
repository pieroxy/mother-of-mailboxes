package net.pieroxy.imf.detection.fcrdns;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FcrdnsEvaluatorTest {

  @Test
  public void passesWhenPtrForwardConfirms() {
    FakeFcrdnsDnsResolver resolver = new FakeFcrdnsDnsResolver()
            .withPtr("203.0.113.10", "mail.example.com")
            .withA("mail.example.com", "203.0.113.10");
    FcrdnsEvaluator evaluator = new FcrdnsEvaluator(resolver);

    assertEquals(FcrdnsResult.PASS, evaluator.evaluate("203.0.113.10"));
  }

  @Test
  public void failsWhenPtrDoesNotForwardConfirm() {
    FakeFcrdnsDnsResolver resolver = new FakeFcrdnsDnsResolver()
            .withPtr("203.0.113.10", "mail.example.com")
            .withA("mail.example.com", "198.51.100.1"); // not the same IP
    FcrdnsEvaluator evaluator = new FcrdnsEvaluator(resolver);

    assertEquals(FcrdnsResult.FAIL, evaluator.evaluate("203.0.113.10"));
  }

  @Test
  public void failsWhenPtrHostnameHasNoForwardRecordAtAll() {
    FakeFcrdnsDnsResolver resolver = new FakeFcrdnsDnsResolver()
            .withPtr("203.0.113.10", "mail.example.com"); // no A record for mail.example.com
    FcrdnsEvaluator evaluator = new FcrdnsEvaluator(resolver);

    assertEquals(FcrdnsResult.FAIL, evaluator.evaluate("203.0.113.10"));
  }

  @Test
  public void noneWhenNoPtrRecord() {
    FcrdnsEvaluator evaluator = new FcrdnsEvaluator(new FakeFcrdnsDnsResolver());

    assertEquals(FcrdnsResult.NONE, evaluator.evaluate("203.0.113.10"));
  }

  @Test
  public void triesEveryPtrNameUntilOneConfirms() {
    FakeFcrdnsDnsResolver resolver = new FakeFcrdnsDnsResolver()
            .withPtr("203.0.113.10", "bogus.example.net", "mail.example.com")
            .withA("bogus.example.net", "198.51.100.99")
            .withA("mail.example.com", "203.0.113.10");
    FcrdnsEvaluator evaluator = new FcrdnsEvaluator(resolver);

    assertEquals(FcrdnsResult.PASS, evaluator.evaluate("203.0.113.10"));
  }

  @Test
  public void ipv6PtrForwardConfirmsViaAaaa() {
    FakeFcrdnsDnsResolver resolver = new FakeFcrdnsDnsResolver()
            .withPtr("2001:db8::1", "mail.example.com")
            .withAaaa("mail.example.com", "2001:0db8:0000:0000:0000:0000:0000:0001"); // same IP, different notation
    FcrdnsEvaluator evaluator = new FcrdnsEvaluator(resolver);

    assertEquals(FcrdnsResult.PASS, evaluator.evaluate("2001:db8::1"));
  }

  @Test
  public void dnsFailureOnPtrLookupIsTempError() {
    FcrdnsEvaluator evaluator = new FcrdnsEvaluator(new FakeFcrdnsDnsResolver().withFailure("203.0.113.10"));

    assertEquals(FcrdnsResult.TEMPERROR, evaluator.evaluate("203.0.113.10"));
  }

  @Test
  public void dnsFailureOnForwardLookupIsTempError() {
    FakeFcrdnsDnsResolver resolver = new FakeFcrdnsDnsResolver()
            .withPtr("203.0.113.10", "mail.example.com")
            .withFailure("mail.example.com");
    FcrdnsEvaluator evaluator = new FcrdnsEvaluator(resolver);

    assertEquals(FcrdnsResult.TEMPERROR, evaluator.evaluate("203.0.113.10"));
  }

  @Test
  public void notAnIpLiteralYieldsNone() {
    FcrdnsEvaluator evaluator = new FcrdnsEvaluator(new FakeFcrdnsDnsResolver());

    assertEquals(FcrdnsResult.NONE, evaluator.evaluate("not-an-ip"));
  }
}
