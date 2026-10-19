package net.pieroxy.mom.diagnostics;

import net.pieroxy.mom.detection.dkim.DkimTestSigner;
import net.pieroxy.mom.detection.dkim.DkimVerifier;
import net.pieroxy.mom.detection.dkim.FakeDkimPublicKeyRecordRetriever;
import net.pieroxy.mom.detection.dmarc.FakeDmarcDnsResolver;
import net.pieroxy.mom.spf.FakeSpfDnsResolver;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DmarcCheckTest {
  private static final String BODY = "Test message sent by MOM.\r\n";

  private static boolean hasFinding(DeliveryCheckResultDto r, CheckStatus status, String fragment) {
    return r.getFindings().stream().anyMatch(f -> f.getStatus() == status && f.getMessage().contains(fragment));
  }

  private static String dump(DeliveryCheckResultDto r) {
    StringBuilder sb = new StringBuilder(r.getStatus() + (r.isPartial() ? " (partial)" : "") + ": " + r.getSummary());
    r.getFacts().forEach(f -> sb.append("\n  ").append(f.getLabel()).append(": ").append(f.getValue()));
    r.getFindings().forEach(f -> sb.append("\n  ").append(f.getStatus()).append(" ").append(f.getMessage()));
    return sb.toString();
  }

  private static DeliveryCheckResultDto dnsOnly(FakeDmarcDnsResolver dmarc, String fromDomain) {
    return new DmarcCheck(dmarc, new FakeSpfDnsResolver(), new DkimVerifier(new FakeDkimPublicKeyRecordRetriever()))
        .run(fromDomain, TestSendOutcome::notConfigured);
  }

  /** A test message from 203.0.113.7, envelope sender {@code envelopeFrom}, optionally signed by {@code dkimDomain}. */
  private static DeliveryCheckResultDto withTestMessage(FakeDmarcDnsResolver dmarc, FakeSpfDnsResolver spf, String envelopeFrom,
                                                        String dkimDomain) throws Exception {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("From", "me@example.org");
    headers.put("Subject", "5bd81a15-9ce3-4f44-9f93-0c7a14604507");
    FakeDkimPublicKeyRecordRetriever keys = new FakeDkimPublicKeyRecordRetriever();
    String raw;
    if (dkimDomain != null) {
      DkimTestSigner.SignedMessage signed = DkimTestSigner.sign("sel1", dkimDomain, headers, BODY);
      keys.withRecord("sel1", dkimDomain, signed.publicKeyRecord);
      raw = signed.rawMessage;
    } else {
      StringBuilder sb = new StringBuilder();
      headers.forEach((k, v) -> sb.append(k).append(": ").append(v).append("\r\n"));
      raw = sb + "\r\n" + BODY;
    }
    String withTrace = "Return-Path: <" + envelopeFrom + ">\r\n"
        + "Received: from out.example.org ([203.0.113.7])\r\n\tby sink.test (MOM SMTP sink) with ESMTP\r\n"
        + "\tfor <mom-test@sink.test>; Sun, 18 Oct 2026 10:00:00 +0200\r\n" + raw;
    TestSendOutcome outcome = TestSendOutcome.received(new ReceivedTestMessage(withTrace.getBytes(StandardCharsets.ISO_8859_1),
        "203.0.113.7", "out.example.org", envelopeFrom, null, 3000));
    return new DmarcCheck(dmarc, spf, new DkimVerifier(keys)).run("example.org", () -> outcome);
  }

  private static FakeSpfDnsResolver spfAllowing(String domain) {
    return new FakeSpfDnsResolver().withTxt(domain, "v=spf1 ip4:203.0.113.7 -all");
  }

  @Test
  public void noRecord() {
    DeliveryCheckResultDto r = dnsOnly(new FakeDmarcDnsResolver(), "example.org");
    assertEquals(dump(r), CheckStatus.WARNING, r.getStatus());
    assertTrue(r.getSummary(), r.getSummary().startsWith("No DMARC record for example.org"));
  }

  @Test
  public void multipleRecords() {
    DeliveryCheckResultDto r = dnsOnly(new FakeDmarcDnsResolver().withTxt("_dmarc.example.org", "v=DMARC1; p=none", "v=DMARC1; p=reject"), "example.org");
    assertEquals(dump(r), CheckStatus.ERROR, r.getStatus());
    assertTrue(dump(r), hasFinding(r, CheckStatus.ERROR, "2 DMARC records"));
  }

  @Test
  public void invalidRecords() {
    assertTrue(hasFinding(dnsOnly(new FakeDmarcDnsResolver().withTxt("_dmarc.example.org", "v=DMARC1; rua=mailto:x@example.org"), "example.org"),
        CheckStatus.ERROR, "no p= tag"));
    assertTrue(hasFinding(dnsOnly(new FakeDmarcDnsResolver().withTxt("_dmarc.example.org", "v=DMARC1; p=block"), "example.org"),
        CheckStatus.ERROR, "p=block is invalid"));
  }

  @Test
  public void recordOnlyIsPartial() {
    DeliveryCheckResultDto r = dnsOnly(new FakeDmarcDnsResolver().withTxt("_dmarc.example.org", "v=DMARC1; p=reject; rua=mailto:d@example.org"), "example.org");
    assertEquals(dump(r), CheckStatus.OK, r.getStatus());
    assertTrue(r.isPartial());
    assertTrue(dump(r), hasFinding(r, CheckStatus.OK, "p=reject"));
  }

  @Test
  public void policyNoneAndPctAndNoRua() {
    DeliveryCheckResultDto none = dnsOnly(new FakeDmarcDnsResolver().withTxt("_dmarc.example.org", "v=DMARC1; p=none"), "example.org");
    assertEquals(dump(none), CheckStatus.WARNING, none.getStatus());
    assertTrue(dump(none), hasFinding(none, CheckStatus.INFO, "No rua= tag"));
    DeliveryCheckResultDto pct = dnsOnly(new FakeDmarcDnsResolver().withTxt("_dmarc.example.org", "v=DMARC1; p=quarantine; pct=20; rua=mailto:d@example.org"), "example.org");
    assertTrue(dump(pct), hasFinding(pct, CheckStatus.WARNING, "pct=20"));
  }

  @Test
  public void subdomainInheritsTheOrganizationalSubdomainPolicy() {
    DeliveryCheckResultDto r = dnsOnly(new FakeDmarcDnsResolver().withTxt("_dmarc.example.org", "v=DMARC1; p=reject; sp=none; rua=mailto:d@example.org"),
        "mail.example.org");
    assertEquals(dump(r), CheckStatus.WARNING, r.getStatus());
    assertTrue(dump(r), hasFinding(r, CheckStatus.WARNING, "p=none"));
    assertTrue(dump(r), r.getFacts().stream().anyMatch(f -> f.getValue().contains("organizational domain")));
  }

  @Test
  public void testMessagePassingThroughSpfAndDkim() throws Exception {
    DeliveryCheckResultDto r = withTestMessage(new FakeDmarcDnsResolver().withTxt("_dmarc.example.org", "v=DMARC1; p=reject; rua=mailto:d@example.org"),
        spfAllowing("example.org"), "me@example.org", "example.org");
    assertEquals(dump(r), CheckStatus.OK, r.getStatus());
    assertFalse(r.isPartial());
    assertEquals("DMARC is set up for example.org (p=reject), and the test message passes it.", r.getSummary());
    assertTrue(dump(r), hasFinding(r, CheckStatus.OK, "through SPF and DKIM"));
  }

  @Test
  public void testMessagePassingThroughSpfOnlyWarnsAboutForwarding() throws Exception {
    DeliveryCheckResultDto r = withTestMessage(new FakeDmarcDnsResolver().withTxt("_dmarc.example.org", "v=DMARC1; p=reject; rua=mailto:d@example.org"),
        spfAllowing("example.org"), "me@example.org", null);
    assertEquals(dump(r), CheckStatus.WARNING, r.getStatus());
    assertTrue(dump(r), hasFinding(r, CheckStatus.OK, "through SPF."));
    assertTrue(dump(r), hasFinding(r, CheckStatus.WARNING, "forwarded"));
  }

  @Test
  public void testMessagePassingThroughDkimDespiteProviderEnvelope() throws Exception {
    DeliveryCheckResultDto r = withTestMessage(new FakeDmarcDnsResolver().withTxt("_dmarc.example.org", "v=DMARC1; p=quarantine; rua=mailto:d@example.org"),
        spfAllowing("bounces.provider.net"), "b-1@bounces.provider.net", "example.org");
    assertEquals(dump(r), CheckStatus.OK, r.getStatus());
    assertTrue(dump(r), hasFinding(r, CheckStatus.OK, "through DKIM"));
  }

  @Test
  public void testMessageFailingWithReject() throws Exception {
    DeliveryCheckResultDto r = withTestMessage(new FakeDmarcDnsResolver().withTxt("_dmarc.example.org", "v=DMARC1; p=reject; rua=mailto:d@example.org"),
        spfAllowing("bounces.provider.net"), "b-1@bounces.provider.net", "provider.net");
    assertEquals(dump(r), CheckStatus.ERROR, r.getStatus());
    assertTrue(dump(r), hasFinding(r, CheckStatus.ERROR, "receiving servers reject your mail"));
  }

  @Test
  public void strictAlignmentRefusesASubdomain() throws Exception {
    DeliveryCheckResultDto r = withTestMessage(new FakeDmarcDnsResolver().withTxt("_dmarc.example.org", "v=DMARC1; p=none; aspf=s; adkim=s; rua=mailto:d@example.org"),
        spfAllowing("mail.example.org"), "b@mail.example.org", "mail.example.org");
    assertTrue(dump(r), hasFinding(r, CheckStatus.WARNING, "fails DMARC"));
  }

  @Test
  public void pendingAndFailedTestSend() {
    FakeDmarcDnsResolver dmarc = new FakeDmarcDnsResolver().withTxt("_dmarc.example.org", "v=DMARC1; p=reject; rua=mailto:d@example.org");
    DmarcCheck check = new DmarcCheck(dmarc, new FakeSpfDnsResolver(), new DkimVerifier(new FakeDkimPublicKeyRecordRetriever()));
    assertEquals(CheckStatus.IN_PROGRESS, check.run("example.org", () -> TestSendOutcome.pending("Waiting…")).getStatus());
    DeliveryCheckResultDto failed = check.run("example.org", () -> TestSendOutcome.failed("SMTP refused it"));
    assertEquals(dump(failed), CheckStatus.WARNING, failed.getStatus());
    assertTrue(failed.isPartial());
  }
}
