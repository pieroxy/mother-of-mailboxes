package net.pieroxy.imf.rules.matchers.implementations;

import net.pieroxy.imf.config.general.MailFilterRuleMatcherConfiguration;
import net.pieroxy.imf.detection.dkim.DkimVerifier;
import net.pieroxy.imf.detection.dmarc.DmarcEvaluator;
import net.pieroxy.imf.detection.dmarc.DmarcMessageEvaluator;
import net.pieroxy.imf.detection.dmarc.FakeDmarcDnsResolver;
import net.pieroxy.imf.detection.dkim.FakeDkimPublicKeyRecordRetriever;
import net.pieroxy.imf.spf.FakeSpfDnsResolver;
import net.pieroxy.imf.detection.spf.SpfEvaluator;
import org.junit.Test;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DmarcPolicyMatcherTest {
  private final Session session = Session.getDefaultInstance(new Properties());

  private MimeMessage parse(String raw) throws MessagingException {
    return new MimeMessage(session, new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
  }

  private DmarcPolicyMatcher matcherWithEvaluators(String key, SpfEvaluator spfEvaluator, DmarcEvaluator dmarcEvaluator) {
    MailFilterRuleMatcherConfiguration config = new MailFilterRuleMatcherConfiguration();
    config.setKey(key);
    DmarcMessageEvaluator evaluator = new DmarcMessageEvaluator(spfEvaluator, new DkimVerifier(new FakeDkimPublicKeyRecordRetriever()), dmarcEvaluator);
    DmarcPolicyMatcher matcher = new DmarcPolicyMatcher(evaluator);
    matcher.setConfig(config);
    return matcher;
  }

  private static String rawMessage() {
    return "From: sender@example.com\r\n"
            + "Return-Path: <sender@example.com>\r\n"
            + "Received: from mail.example.com (mail.example.com [203.0.113.10])\r\n"
            + "\tby mx.myprovider.com with SMTPS id abc; Mon, 31 Aug 2026 10:00:00 +0000\r\n"
            + "Subject: Test\r\n\r\nHello\r\n";
  }

  @Test
  public void matchesTheDomainsPublishedPolicy() throws Exception {
    SpfEvaluator spfEvaluator = new SpfEvaluator(new FakeSpfDnsResolver()); // doesn't matter: the policy doesn't depend on pass/fail
    DmarcEvaluator dmarcEvaluator = new DmarcEvaluator(new FakeDmarcDnsResolver()
            .withTxt("_dmarc.example.com", "v=DMARC1; p=reject"));
    DmarcPolicyMatcher matcher = matcherWithEvaluators("reject", spfEvaluator, dmarcEvaluator);

    assertTrue(matcher.matches(parse(rawMessage())).matched());
  }

  @Test
  public void unpublishedIsDistinctFromExplicitNonePolicy() throws Exception {
    SpfEvaluator spfEvaluator = new SpfEvaluator(new FakeSpfDnsResolver());
    // No _dmarc.example.com record at all: "unpublished", not "none".
    DmarcEvaluator dmarcEvaluator = new DmarcEvaluator(new FakeDmarcDnsResolver());
    DmarcPolicyMatcher matcher = matcherWithEvaluators("unpublished", spfEvaluator, dmarcEvaluator);

    assertTrue(matcher.matches(parse(rawMessage())).matched());

    DmarcPolicyMatcher matcherForNone = matcherWithEvaluators("none", spfEvaluator, dmarcEvaluator);
    assertFalse(matcherForNone.matches(parse(rawMessage())).matched());
  }

  @Test
  public void explicitPolicyNoneMatchesNoneNotUnpublished() throws Exception {
    SpfEvaluator spfEvaluator = new SpfEvaluator(new FakeSpfDnsResolver());
    DmarcEvaluator dmarcEvaluator = new DmarcEvaluator(new FakeDmarcDnsResolver()
            .withTxt("_dmarc.example.com", "v=DMARC1; p=none"));

    assertTrue(matcherWithEvaluators("none", spfEvaluator, dmarcEvaluator).matches(parse(rawMessage())).matched());
    assertFalse(matcherWithEvaluators("unpublished", spfEvaluator, dmarcEvaluator).matches(parse(rawMessage())).matched());
  }

  @Test
  public void usesSubdomainPolicyWhenRecordFoundViaOrganizationalDomain() throws Exception {
    SpfEvaluator spfEvaluator = new SpfEvaluator(new FakeSpfDnsResolver());
    DmarcEvaluator dmarcEvaluator = new DmarcEvaluator(new FakeDmarcDnsResolver()
            .withTxt("_dmarc.example.com", "v=DMARC1; p=reject; sp=quarantine"));
    MailFilterRuleMatcherConfiguration config = new MailFilterRuleMatcherConfiguration();
    config.setKey("quarantine");
    DmarcMessageEvaluator evaluator = new DmarcMessageEvaluator(spfEvaluator,
            new DkimVerifier(new FakeDkimPublicKeyRecordRetriever()), dmarcEvaluator);
    DmarcPolicyMatcher matcher = new DmarcPolicyMatcher(evaluator);
    matcher.setConfig(config);

    String raw = "From: sender@news.example.com\r\n"
            + "Return-Path: <sender@news.example.com>\r\n"
            + "Subject: Test\r\n\r\nHello\r\n";

    assertTrue(matcher.matches(parse(raw)).matched());
  }

  @Test
  public void extractKeyFromExampleReturnsThePolicy() throws Exception {
    SpfEvaluator spfEvaluator = new SpfEvaluator(new FakeSpfDnsResolver());
    DmarcEvaluator dmarcEvaluator = new DmarcEvaluator(new FakeDmarcDnsResolver()
            .withTxt("_dmarc.example.com", "v=DMARC1; p=quarantine"));
    DmarcMessageEvaluator evaluator = new DmarcMessageEvaluator(spfEvaluator,
            new DkimVerifier(new FakeDkimPublicKeyRecordRetriever()), dmarcEvaluator);
    DmarcPolicyMatcher matcher = new DmarcPolicyMatcher(evaluator);

    assertEquals("quarantine", matcher.extractKeyFromExample(parse(rawMessage())));
  }
}
