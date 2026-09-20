package net.pieroxy.imf.rules.matchers.implementations;

import net.pieroxy.imf.config.MailFilterRuleMatcherConfiguration;
import net.pieroxy.imf.dkim.DkimTestSigner;
import net.pieroxy.imf.detection.dkim.DkimVerifier;
import net.pieroxy.imf.dkim.FakeDkimPublicKeyRecordRetriever;
import org.junit.Test;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DkimResultMatcherTest {
  private final Session session = Session.getDefaultInstance(new Properties());

  private static Map<String, String> defaultHeaders() {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("From", "sender@example.com");
    headers.put("To", "recipient@example.com");
    headers.put("Subject", "Test");
    return headers;
  }

  private MimeMessage parse(String raw) throws MessagingException {
    return new MimeMessage(session, new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
  }

  private DkimResultMatcher matcherWithVerifier(String key, DkimVerifier verifier) {
    MailFilterRuleMatcherConfiguration config = new MailFilterRuleMatcherConfiguration();
    config.setKey(key);
    DkimResultMatcher matcher = new DkimResultMatcher(verifier);
    matcher.setConfig(config);
    return matcher;
  }

  @Test
  public void matchesOnValidSignature() throws Exception {
    DkimTestSigner.SignedMessage signed = DkimTestSigner.sign("sel1", "example.com", defaultHeaders(), "Hello world\r\n");
    FakeDkimPublicKeyRecordRetriever retriever = new FakeDkimPublicKeyRecordRetriever()
            .withRecord("sel1", "example.com", signed.publicKeyRecord);
    DkimResultMatcher matcher = matcherWithVerifier("pass", new DkimVerifier(retriever));

    assertTrue(matcher.matches(parse(signed.rawMessage)).matched());
  }

  @Test
  public void doesNotMatchWhenKeyDiffers() throws Exception {
    DkimTestSigner.SignedMessage signed = DkimTestSigner.sign("sel1", "example.com", defaultHeaders(), "Hello world\r\n");
    FakeDkimPublicKeyRecordRetriever retriever = new FakeDkimPublicKeyRecordRetriever()
            .withRecord("sel1", "example.com", signed.publicKeyRecord);
    DkimResultMatcher matcher = matcherWithVerifier("fail", new DkimVerifier(retriever));

    assertFalse(matcher.matches(parse(signed.rawMessage)).matched());
  }

  @Test
  public void matchesNoneWhenNoDkimSignatureHeader() throws Exception {
    String raw = "From: sender@example.com\r\nTo: recipient@example.com\r\nSubject: Test\r\n\r\nHello world\r\n";
    DkimResultMatcher matcher = matcherWithVerifier("none", new DkimVerifier(new FakeDkimPublicKeyRecordRetriever()));

    assertTrue(matcher.matches(parse(raw)).matched());
  }

  @Test
  public void extractKeyFromExampleReturnsDkimResult() throws Exception {
    DkimTestSigner.SignedMessage signed = DkimTestSigner.sign("sel1", "example.com", defaultHeaders(), "Hello world\r\n");
    FakeDkimPublicKeyRecordRetriever retriever = new FakeDkimPublicKeyRecordRetriever()
            .withRecord("sel1", "example.com", signed.publicKeyRecord);
    DkimResultMatcher matcher = new DkimResultMatcher(new DkimVerifier(retriever));

    assertEquals("pass", matcher.extractKeyFromExample(parse(signed.rawMessage)));
  }

  @Test
  public void extractKeyFromExampleNeverThrowsEvenWithoutSignature() throws Exception {
    String raw = "From: sender@example.com\r\nTo: recipient@example.com\r\nSubject: Test\r\n\r\nHello world\r\n";
    DkimResultMatcher matcher = new DkimResultMatcher(new DkimVerifier(new FakeDkimPublicKeyRecordRetriever()));

    assertEquals("none", matcher.extractKeyFromExample(parse(raw)));
  }
}
