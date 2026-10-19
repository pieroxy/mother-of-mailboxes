package net.pieroxy.mom.diagnostics;

import net.pieroxy.mom.detection.dkim.DkimTestSigner;
import net.pieroxy.mom.detection.dkim.FakeDkimPublicKeyRecordRetriever;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DkimCheckTest {
  private static final String BODY = "Test message sent by MOM.\r\n";

  private static Map<String, String> headers() {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("From", "me@example.org");
    headers.put("To", "mom-test@sink.test");
    headers.put("Subject", "5bd81a15-9ce3-4f44-9f93-0c7a14604507");
    return headers;
  }

  /** The message as the sink hands it back: its own trace headers on top of what was signed. */
  private static TestSendOutcome received(String raw) {
    String withTrace = "Return-Path: <me@example.org>\r\n"
        + "Received: from out.example.org ([203.0.113.7])\r\n"
        + "\tby sink.test (MOM SMTP sink) with ESMTP\r\n"
        + "\tfor <mom-test@sink.test>; Sun, 18 Oct 2026 10:00:00 +0200\r\n" + raw;
    return TestSendOutcome.received(new ReceivedTestMessage(withTrace.getBytes(StandardCharsets.ISO_8859_1),
        "203.0.113.7", "out.example.org", "me@example.org", null, 3000));
  }

  private static DeliveryCheckResultDto run(FakeDkimPublicKeyRecordRetriever keys, TestSendOutcome outcome) {
    return new DkimCheck(keys).run("example.org", () -> outcome);
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
  public void validAlignedSignature() throws Exception {
    DkimTestSigner.SignedMessage signed = DkimTestSigner.sign("sel1", "example.org", headers(), BODY);
    DeliveryCheckResultDto r = run(new FakeDkimPublicKeyRecordRetriever().withRecord("sel1", "example.org", signed.publicKeyRecord),
        received(signed.rawMessage));
    assertEquals(dump(r), CheckStatus.OK, r.getStatus());
    assertEquals("Your mail is DKIM-signed by example.org, with a valid signature.", r.getSummary());
    assertTrue(dump(r), hasFinding(r, CheckStatus.OK, "signature by example.org (selector sel1) is valid"));
    assertTrue(dump(r), hasFinding(r, CheckStatus.OK, "2048 bits"));
  }

  @Test
  public void subdomainSignatureIsAligned() throws Exception {
    DkimTestSigner.SignedMessage signed = DkimTestSigner.sign("sel1", "mail.example.org", headers(), BODY);
    DeliveryCheckResultDto r = run(new FakeDkimPublicKeyRecordRetriever().withRecord("sel1", "mail.example.org", signed.publicKeyRecord),
        received(signed.rawMessage));
    assertEquals(dump(r), CheckStatus.OK, r.getStatus());
  }

  @Test
  public void unsignedMessage() {
    StringBuilder raw = new StringBuilder();
    headers().forEach((k, v) -> raw.append(k).append(": ").append(v).append("\r\n"));
    DeliveryCheckResultDto r = run(new FakeDkimPublicKeyRecordRetriever(), received(raw + "\r\n" + BODY));
    assertEquals(dump(r), CheckStatus.ERROR, r.getStatus());
    assertTrue(r.getSummary(), r.getSummary().startsWith("The test message isn't DKIM-signed"));
  }

  @Test
  public void signedOnlyByTheProvider() throws Exception {
    DkimTestSigner.SignedMessage signed = DkimTestSigner.sign("s1", "provider.net", headers(), BODY);
    DeliveryCheckResultDto r = run(new FakeDkimPublicKeyRecordRetriever().withRecord("s1", "provider.net", signed.publicKeyRecord),
        received(signed.rawMessage));
    assertEquals(dump(r), CheckStatus.WARNING, r.getStatus());
    assertTrue(dump(r), hasFinding(r, CheckStatus.INFO, "isn't by example.org"));
    assertTrue(dump(r), hasFinding(r, CheckStatus.WARNING, "No valid signature by example.org"));
  }

  @Test
  public void wrongPublishedKey() throws Exception {
    DkimTestSigner.SignedMessage signed = DkimTestSigner.sign("sel1", "example.org", headers(), BODY);
    DkimTestSigner.SignedMessage other = DkimTestSigner.sign("sel1", "example.org", headers(), BODY);
    DeliveryCheckResultDto r = run(new FakeDkimPublicKeyRecordRetriever().withRecord("sel1", "example.org", other.publicKeyRecord),
        received(signed.rawMessage));
    assertEquals(dump(r), CheckStatus.ERROR, r.getStatus());
    assertTrue(dump(r), hasFinding(r, CheckStatus.ERROR, "doesn't verify"));
  }

  @Test
  public void unpublishedKey() throws Exception {
    DkimTestSigner.SignedMessage signed = DkimTestSigner.sign("sel1", "example.org", headers(), BODY);
    DeliveryCheckResultDto r = run(new FakeDkimPublicKeyRecordRetriever(), received(signed.rawMessage));
    assertEquals(dump(r), CheckStatus.ERROR, r.getStatus());
    assertTrue(dump(r), hasFinding(r, CheckStatus.ERROR, "no DKIM key published at sel1._domainkey.example.org"));
  }

  @Test
  public void weakKeyAndTestingMode() throws Exception {
    DkimTestSigner.SignedMessage signed = DkimTestSigner.sign("sel1", "example.org", headers(), BODY, 1024, "sha256", "");
    DeliveryCheckResultDto r = run(new FakeDkimPublicKeyRecordRetriever().withRecord("sel1", "example.org", signed.publicKeyRecord + "; t=y"),
        received(signed.rawMessage));
    assertEquals(dump(r), CheckStatus.WARNING, r.getStatus());
    assertTrue(dump(r), hasFinding(r, CheckStatus.WARNING, "is 1024 bits"));
    assertTrue(dump(r), hasFinding(r, CheckStatus.WARNING, "testing mode"));
  }

  @Test
  public void bodyLengthLimit() throws Exception {
    DkimTestSigner.SignedMessage signed = DkimTestSigner.sign("sel1", "example.org", headers(), BODY, 2048, "sha256",
        "l=" + BODY.length() + "; ");
    DeliveryCheckResultDto r = run(new FakeDkimPublicKeyRecordRetriever().withRecord("sel1", "example.org", signed.publicKeyRecord),
        received(signed.rawMessage));
    assertTrue(dump(r), hasFinding(r, CheckStatus.WARNING, "(l= tag)"));
  }

  @Test
  public void sha1() throws Exception {
    DkimTestSigner.SignedMessage signed = DkimTestSigner.sign("sel1", "example.org", headers(), BODY, 2048, "sha1", "");
    DeliveryCheckResultDto r = run(new FakeDkimPublicKeyRecordRetriever().withRecord("sel1", "example.org", signed.publicKeyRecord),
        received(signed.rawMessage));
    assertTrue(dump(r), hasFinding(r, CheckStatus.WARNING, "SHA-1 is obsolete"));
  }

  @Test
  public void withoutATestMessage() {
    assertEquals(CheckStatus.SKIPPED, run(new FakeDkimPublicKeyRecordRetriever(), TestSendOutcome.notConfigured()).getStatus());
    DeliveryCheckResultDto failed = run(new FakeDkimPublicKeyRecordRetriever(), TestSendOutcome.failed("SMTP refused it"));
    assertEquals(CheckStatus.SKIPPED, failed.getStatus());
    assertTrue(failed.getSummary(), failed.getSummary().contains("SMTP refused it"));
    assertEquals(CheckStatus.IN_PROGRESS, run(new FakeDkimPublicKeyRecordRetriever(), TestSendOutcome.pending("Waiting…")).getStatus());
  }
}
