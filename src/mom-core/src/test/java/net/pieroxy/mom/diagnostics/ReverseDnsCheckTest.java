package net.pieroxy.mom.diagnostics;

import net.pieroxy.mom.detection.fcrdns.FakeFcrdnsDnsResolver;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReverseDnsCheckTest {
  private static DeliveryCheckResultDto run(FakeFcrdnsDnsResolver dns, String ip, String helo) {
    TestSendOutcome outcome = TestSendOutcome.received(new ReceivedTestMessage(new byte[0], ip, helo, "me@example.org", null, 3000));
    return new ReverseDnsCheck(dns).run(() -> outcome);
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
  public void everythingConsistent() {
    FakeFcrdnsDnsResolver dns = new FakeFcrdnsDnsResolver()
        .withPtr("203.0.113.7", "mail.example.org.")
        .withA("mail.example.org", "203.0.113.7");
    DeliveryCheckResultDto r = run(dns, "203.0.113.7", "mail.example.org");
    assertEquals(dump(r), CheckStatus.OK, r.getStatus());
    assertEquals("The server sending your mail has a proper reverse DNS name (mail.example.org), consistent with its HELO name.", r.getSummary());
    assertTrue(dump(r), hasFinding(r, CheckStatus.OK, "matches the reverse DNS name"));
  }

  @Test
  public void ipv6() {
    FakeFcrdnsDnsResolver dns = new FakeFcrdnsDnsResolver()
        .withPtr("2001:db8::25", "mail.example.org")
        .withAaaa("mail.example.org", "2001:db8:0:0:0:0:0:25");
    assertEquals(CheckStatus.OK, run(dns, "2001:db8::25", "mail.example.org").getStatus());
  }

  @Test
  public void noPtr() {
    DeliveryCheckResultDto r = run(new FakeFcrdnsDnsResolver().withA("mail.example.org", "203.0.113.7"), "203.0.113.7", "mail.example.org");
    assertEquals(dump(r), CheckStatus.ERROR, r.getStatus());
    assertTrue(dump(r), hasFinding(r, CheckStatus.ERROR, "has no reverse DNS"));
    assertTrue(dump(r), hasFinding(r, CheckStatus.OK, "resolves to 203.0.113.7"));
  }

  @Test
  public void ptrNotConfirmed() {
    FakeFcrdnsDnsResolver dns = new FakeFcrdnsDnsResolver()
        .withPtr("203.0.113.7", "mail.example.org")
        .withA("mail.example.org", "198.51.100.1");
    DeliveryCheckResultDto r = run(dns, "203.0.113.7", "mail.example.org");
    assertEquals(dump(r), CheckStatus.ERROR, r.getStatus());
    assertTrue(dump(r), hasFinding(r, CheckStatus.ERROR, "doesn't resolve back"));
  }

  @Test
  public void genericResidentialName() {
    FakeFcrdnsDnsResolver dns = new FakeFcrdnsDnsResolver()
        .withPtr("82.65.93.243", "82-65-93-243.subs.proxad.net")
        .withA("82-65-93-243.subs.proxad.net", "82.65.93.243");
    DeliveryCheckResultDto r = run(dns, "82.65.93.243", "82-65-93-243.subs.proxad.net");
    assertEquals(dump(r), CheckStatus.WARNING, r.getStatus());
    assertTrue(dump(r), hasFinding(r, CheckStatus.WARNING, "residential or dynamic"));
  }

  @Test
  public void looksGeneric() {
    assertTrue(ReverseDnsCheck.looksGeneric("243.93.65.82.example.net", "82.65.93.243"));
    assertTrue(ReverseDnsCheck.looksGeneric("abc.dynamic.isp.net", "10.0.0.1"));
    assertTrue(ReverseDnsCheck.looksGeneric("dsl-12.isp.net", "10.0.0.1"));
    assertFalse(ReverseDnsCheck.looksGeneric("mail.example.org", "82.65.93.243"));
    assertFalse(ReverseDnsCheck.looksGeneric("host1.hosting.example", "10.0.0.1"));
    assertFalse(ReverseDnsCheck.looksGeneric("mail-out.provider.net", "10.0.0.1"));
  }

  @Test
  public void badHeloNames() {
    FakeFcrdnsDnsResolver dns = new FakeFcrdnsDnsResolver()
        .withPtr("203.0.113.7", "mail.example.org")
        .withA("mail.example.org", "203.0.113.7");
    assertTrue(hasFinding(run(dns, "203.0.113.7", "localhost"), CheckStatus.ERROR, "isn't a public fully qualified domain name"));
    assertTrue(hasFinding(run(dns, "203.0.113.7", "pieroxy-dev"), CheckStatus.ERROR, "\"pieroxy-dev\", which isn't a public"));
    assertTrue(hasFinding(run(dns, "203.0.113.7", "box.lan"), CheckStatus.ERROR, "Configure it to use mail.example.org"));
    assertTrue(hasFinding(run(dns, "203.0.113.7", "[203.0.113.7]"), CheckStatus.WARNING, "with an IP address"));
  }

  @Test
  public void heloResolvingButDifferent() {
    FakeFcrdnsDnsResolver dns = new FakeFcrdnsDnsResolver()
        .withPtr("203.0.113.7", "mail.example.org")
        .withA("mail.example.org", "203.0.113.7")
        .withA("smtp.example.org", "203.0.113.7");
    DeliveryCheckResultDto r = run(dns, "203.0.113.7", "smtp.example.org");
    assertEquals(dump(r), CheckStatus.OK, r.getStatus());
    assertTrue(dump(r), hasFinding(r, CheckStatus.INFO, "differs from the reverse DNS name"));
  }

  @Test
  public void heloNotResolving() {
    FakeFcrdnsDnsResolver dns = new FakeFcrdnsDnsResolver()
        .withPtr("203.0.113.7", "mail.example.org")
        .withA("mail.example.org", "203.0.113.7");
    DeliveryCheckResultDto r = run(dns, "203.0.113.7", "other.example.org");
    assertEquals(dump(r), CheckStatus.WARNING, r.getStatus());
    assertTrue(dump(r), hasFinding(r, CheckStatus.WARNING, "doesn't resolve, nor matches"));
  }

  @Test
  public void withoutATestMessage() {
    ReverseDnsCheck check = new ReverseDnsCheck(new FakeFcrdnsDnsResolver());
    assertEquals(CheckStatus.SKIPPED, check.run(TestSendOutcome::notConfigured).getStatus());
    assertEquals(CheckStatus.SKIPPED, check.run(() -> TestSendOutcome.failed("nope")).getStatus());
    assertEquals(CheckStatus.IN_PROGRESS, check.run(() -> TestSendOutcome.pending("Waiting…")).getStatus());
  }
}
