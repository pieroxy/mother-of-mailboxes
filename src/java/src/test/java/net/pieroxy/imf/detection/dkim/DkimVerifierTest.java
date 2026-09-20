package net.pieroxy.imf.detection.dkim;

import net.pieroxy.imf.detection.dkim.DkimResult;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class DkimVerifierTest {

  private static Map<String, String> defaultHeaders() {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("From", "sender@example.com");
    headers.put("To", "recipient@example.com");
    headers.put("Subject", "Test");
    return headers;
  }

  private static DkimResult verify(String rawMessage, FakeDkimPublicKeyRecordRetriever retriever) {
    return new DkimVerifier(retriever).verify(new ByteArrayInputStream(rawMessage.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  public void validSignaturePasses() throws Exception {
    DkimTestSigner.SignedMessage signed = DkimTestSigner.sign("sel1", "example.com", defaultHeaders(), "Hello world\r\n");
    FakeDkimPublicKeyRecordRetriever retriever = new FakeDkimPublicKeyRecordRetriever()
            .withRecord("sel1", "example.com", signed.publicKeyRecord);

    assertEquals(DkimResult.PASS, verify(signed.rawMessage, retriever));
  }

  @Test
  public void tamperedBodyFails() throws Exception {
    DkimTestSigner.SignedMessage signed = DkimTestSigner.sign("sel1", "example.com", defaultHeaders(), "Hello world\r\n");
    String tampered = signed.rawMessage.replace("Hello world", "Goodbye world");
    FakeDkimPublicKeyRecordRetriever retriever = new FakeDkimPublicKeyRecordRetriever()
            .withRecord("sel1", "example.com", signed.publicKeyRecord);

    assertEquals(DkimResult.FAIL, verify(tampered, retriever));
  }

  @Test
  public void tamperedSignedHeaderFails() throws Exception {
    DkimTestSigner.SignedMessage signed = DkimTestSigner.sign("sel1", "example.com", defaultHeaders(), "Hello world\r\n");
    String tampered = signed.rawMessage.replace("sender@example.com", "attacker@evil.example");
    FakeDkimPublicKeyRecordRetriever retriever = new FakeDkimPublicKeyRecordRetriever()
            .withRecord("sel1", "example.com", signed.publicKeyRecord);

    assertEquals(DkimResult.FAIL, verify(tampered, retriever));
  }

  @Test
  public void noDkimSignatureHeaderYieldsNone() {
    String raw = "From: sender@example.com\r\nTo: recipient@example.com\r\nSubject: Test\r\n\r\nHello world\r\n";

    assertEquals(DkimResult.NONE, verify(raw, new FakeDkimPublicKeyRecordRetriever()));
  }

  @Test
  public void unknownSigningDomainDoesNotPass() throws Exception {
    DkimTestSigner.SignedMessage signed = DkimTestSigner.sign("sel1", "example.com", defaultHeaders(), "Hello world\r\n");
    // The retriever doesn't know about this selector/domain: the public key can't be found.
    FakeDkimPublicKeyRecordRetriever retriever = new FakeDkimPublicKeyRecordRetriever();

    assertNotEquals(DkimResult.PASS, verify(signed.rawMessage, retriever));
  }

  @Test
  public void oneValidSignatureAmongMultipleIsEnoughToPass() throws Exception {
    DkimTestSigner.SignedMessage validSigned = DkimTestSigner.sign("sel1", "example.com", defaultHeaders(), "Hello world\r\n");
    // A second signature, broken (wrong domain, key not found by the retriever), added before
    // the valid one: it must not prevent the overall pass.
    String brokenSignature = "DKIM-Signature: v=1; a=rsa-sha256; c=simple/simple; d=unknown.example; s=sel1; "
            + "h=From:To:Subject; bh=AAAA; b=AAAA\r\n";
    String rawWithTwoSignatures = brokenSignature + validSigned.rawMessage;

    FakeDkimPublicKeyRecordRetriever retriever = new FakeDkimPublicKeyRecordRetriever()
            .withRecord("sel1", "example.com", validSigned.publicKeyRecord);

    assertEquals(DkimResult.PASS, verify(rawWithTwoSignatures, retriever));
  }
}
