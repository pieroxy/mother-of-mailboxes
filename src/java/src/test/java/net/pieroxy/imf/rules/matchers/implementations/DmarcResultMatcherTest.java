package net.pieroxy.imf.rules.matchers.implementations;

import net.pieroxy.imf.config.MailFilterRuleMatcherConfiguration;
import net.pieroxy.imf.dkim.DkimTestSigner;
import net.pieroxy.imf.detection.dkim.DkimVerifier;
import net.pieroxy.imf.dkim.FakeDkimPublicKeyRecordRetriever;
import net.pieroxy.imf.detection.dmarc.DmarcEvaluator;
import net.pieroxy.imf.detection.dmarc.DmarcMessageEvaluator;
import net.pieroxy.imf.dmarc.FakeDmarcDnsResolver;
import net.pieroxy.imf.spf.FakeSpfDnsResolver;
import net.pieroxy.imf.detection.spf.SpfEvaluator;
import org.junit.Test;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DmarcResultMatcherTest {
  private final Session session = Session.getDefaultInstance(new Properties());

  private MimeMessage parse(String raw) throws MessagingException {
    return new MimeMessage(session, new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
  }

  private DmarcResultMatcher matcherWithEvaluators(String key, SpfEvaluator spfEvaluator,
                                                    FakeDkimPublicKeyRecordRetriever retriever,
                                                    DmarcEvaluator dmarcEvaluator) {
    MailFilterRuleMatcherConfiguration config = new MailFilterRuleMatcherConfiguration();
    config.setKey(key);
    DmarcMessageEvaluator evaluator = new DmarcMessageEvaluator(spfEvaluator, new DkimVerifier(retriever), dmarcEvaluator);
    DmarcResultMatcher matcher = new DmarcResultMatcher(evaluator);
    matcher.setConfig(config);
    return matcher;
  }

  @Test
  public void matchesViaAlignedSpfWhenDkimAbsent() throws Exception {
    SpfEvaluator spfEvaluator = new SpfEvaluator(new FakeSpfDnsResolver()
            .withTxt("example.com", "v=spf1 ip4:203.0.113.10/32 -all"));
    DmarcEvaluator dmarcEvaluator = new DmarcEvaluator(new FakeDmarcDnsResolver()
            .withTxt("_dmarc.example.com", "v=DMARC1; p=reject"));
    DmarcResultMatcher matcher = matcherWithEvaluators("pass", spfEvaluator,
            new FakeDkimPublicKeyRecordRetriever(), dmarcEvaluator);

    String raw = "From: sender@example.com\r\n"
            + "Return-Path: <sender@example.com>\r\n"
            + "Received: from mail.example.com (mail.example.com [203.0.113.10])\r\n"
            + "\tby mx.myprovider.com with SMTPS id abc; Mon, 31 Aug 2026 10:00:00 +0000\r\n"
            + "Subject: Test\r\n\r\nHello\r\n";

    assertTrue(matcher.matches(parse(raw)).matched());
  }

  @Test
  public void matchesViaAlignedDkimWhenSpfUnavailable() throws Exception {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("From", "sender@example.com");
    headers.put("Subject", "Test");
    DkimTestSigner.SignedMessage signed = DkimTestSigner.sign("sel1", "example.com", headers, "Hello world\r\n");

    SpfEvaluator spfEvaluator = new SpfEvaluator(new FakeSpfDnsResolver()); // pas de Received : SPF ne passera jamais
    FakeDkimPublicKeyRecordRetriever retriever = new FakeDkimPublicKeyRecordRetriever()
            .withRecord("sel1", "example.com", signed.publicKeyRecord);
    DmarcEvaluator dmarcEvaluator = new DmarcEvaluator(new FakeDmarcDnsResolver()
            .withTxt("_dmarc.example.com", "v=DMARC1; p=reject"));
    DmarcResultMatcher matcher = matcherWithEvaluators("pass", spfEvaluator, retriever, dmarcEvaluator);

    assertTrue(matcher.matches(parse(signed.rawMessage)).matched());
  }

  @Test
  public void doesNotMatchWhenNoSingleFromAddress() throws Exception {
    SpfEvaluator spfEvaluator = new SpfEvaluator(new FakeSpfDnsResolver());
    DmarcEvaluator dmarcEvaluator = new DmarcEvaluator(new FakeDmarcDnsResolver());
    DmarcResultMatcher matcher = matcherWithEvaluators("pass", spfEvaluator,
            new FakeDkimPublicKeyRecordRetriever(), dmarcEvaluator);

    String raw = "Subject: Test\r\n\r\nHello\r\n"; // pas de From du tout

    assertFalse(matcher.matches(parse(raw)).matched());
  }
}
