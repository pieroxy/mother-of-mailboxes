package net.pieroxy.mom.diagnostics;

import net.pieroxy.mom.spf.FakeSpfDnsResolver;
import org.junit.Test;

import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MtaStsCheckTest {
  private static final X509Certificate CA = load("mta-sts-test-ca.pem");
  /** Signed by CA, valid for mx1.example.org and *.mail.example.org. */
  private static final X509Certificate MX = load("mta-sts-test-mx.pem");
  /** Self-signed, untrusted. */
  private static final X509Certificate OTHER = load("dane-test-cert-1.pem");
  private static final X509TrustManager TRUST_TEST_CA = trusting(CA);

  private static X509Certificate load(String name) {
    try (InputStream in = MtaStsCheckTest.class.getResourceAsStream("/diagnostics/" + name)) {
      return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static X509TrustManager trusting(X509Certificate ca) {
    try {
      KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
      ks.load(null, null);
      ks.setCertificateEntry("ca", ca);
      TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      tmf.init(ks);
      return (X509TrustManager) tmf.getTrustManagers()[0];
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static class FakeMx implements MxDnsResolver {
    final List<Mx> mx = new ArrayList<>();

    public List<Mx> lookupMx(String domain) {
      return mx;
    }

    public List<String> lookupCname(String name) {
      return List.of();
    }

    public List<String> lookupA(String name) {
      return List.of();
    }

    public List<String> lookupAaaa(String name) {
      return List.of();
    }
  }

  private static final String GOOD_POLICY = "version: STSv1\r\nmode: enforce\r\nmx: mx1.example.org\r\nmx: *.mail.example.org\r\nmax_age: 604800\r\n";

  private static final StartTlsProbe.CertificateSource SERVERS = host -> switch (host) {
    case "mx1.example.org", "a.mail.example.org" -> List.of(MX, CA);
    case "mx2.example.org" -> List.of(MX, CA); // Valid certificate, wrong name.
    case "self.example.org" -> List.of(OTHER);
    case "plain.example.org" -> throw new StartTlsProbe.NoStartTlsException("no STARTTLS");
    default -> throw new IOException("Connect timed out");
  };

  private static DeliveryCheckResultDto run(FakeSpfDnsResolver txt, MtaStsCheck.PolicyFetcher policy, String... mxHosts) {
    FakeMx mx = new FakeMx();
    for (int i = 0; i < mxHosts.length; i++) mx.mx.add(new MxDnsResolver.Mx(10 * (i + 1), mxHosts[i]));
    return new MtaStsCheck(txt, mx, policy, SERVERS, TRUST_TEST_CA).run("example.org");
  }

  private static MtaStsCheck.PolicyFetcher serving(String body) {
    return domain -> new MtaStsCheck.PolicyResponse(200, "text/plain; charset=utf-8", body);
  }

  private static FakeSpfDnsResolver records(boolean tlsRpt) {
    FakeSpfDnsResolver txt = new FakeSpfDnsResolver().withTxt("_mta-sts.example.org", "v=STSv1; id=20261019T120000");
    if (tlsRpt) txt.withTxt("_smtp._tls.example.org", "v=TLSRPTv1; rua=mailto:tls@example.org");
    return txt;
  }

  private static boolean hasFinding(DeliveryCheckResultDto r, CheckStatus status, String fragment) {
    return r.getFindings().stream().anyMatch(f -> f.getStatus() == status && f.getMessage().contains(fragment));
  }

  private static String dump(DeliveryCheckResultDto r) {
    StringBuilder sb = new StringBuilder(r.getStatus() + (r.isPartial() ? " (partial)" : "") + ": " + r.getSummary());
    r.getFacts().forEach(f -> sb.append("\n  ").append(f.getLabel()).append(": ").append(f.getValue()));
    r.getFindings().forEach(f -> sb.append("\n  ").append(f.getStatus()).append(" ").append(f.getMessage()));
    return sb.toString();
  }

  @Test
  public void neitherSetUp() {
    DeliveryCheckResultDto r = run(new FakeSpfDnsResolver(), serving(""), "mx1.example.org");
    assertEquals(dump(r), CheckStatus.INFO, r.getStatus());
    assertEquals("Neither MTA-STS nor TLS-RPT is set up for example.org (optional).", r.getSummary());
  }

  @Test
  public void fullySetUp() {
    DeliveryCheckResultDto r = run(records(true), serving(GOOD_POLICY), "mx1.example.org", "a.mail.example.org");
    assertEquals(dump(r), CheckStatus.OK, r.getStatus());
    assertFalse(r.isPartial());
    assertEquals("MTA-STS is set up for example.org (mode enforce), with TLS-RPT reporting.", r.getSummary());
    assertTrue(dump(r), hasFinding(r, CheckStatus.OK, "a.mail.example.org presents a valid certificate"));
    assertTrue(dump(r), hasFinding(r, CheckStatus.OK, "TLS-RPT is set up"));
  }

  @Test
  public void mtaStsWithoutTlsRptSuggestsIt() {
    DeliveryCheckResultDto r = run(records(false), serving(GOOD_POLICY), "mx1.example.org");
    assertEquals(dump(r), CheckStatus.OK, r.getStatus());
    assertTrue(dump(r), hasFinding(r, CheckStatus.INFO, "a broken MTA-STS setup would go unnoticed"));
  }

  @Test
  public void mxNotInPolicyAndBadCertificates() {
    DeliveryCheckResultDto r = run(records(true), serving(GOOD_POLICY), "mx1.example.org", "mx2.example.org", "self.example.org", "plain.example.org");
    assertEquals(dump(r), CheckStatus.ERROR, r.getStatus());
    assertTrue(dump(r), hasFinding(r, CheckStatus.ERROR, "mx2.example.org matches none of the policy's mx entries"));
    assertTrue(dump(r), hasFinding(r, CheckStatus.ERROR, "mx2.example.org's certificate isn't valid for the name"));
    assertTrue(dump(r), hasFinding(r, CheckStatus.ERROR, "self.example.org's certificate isn't trusted"));
    assertTrue(dump(r), hasFinding(r, CheckStatus.ERROR, "plain.example.org doesn't offer STARTTLS"));
  }

  @Test
  public void testingModeOnlyWarns() {
    DeliveryCheckResultDto r = run(records(true), serving(GOOD_POLICY.replace("enforce", "testing")), "mx1.example.org", "mx2.example.org");
    assertEquals(dump(r), CheckStatus.WARNING, r.getStatus());
    assertTrue(dump(r), hasFinding(r, CheckStatus.WARNING, "Mode testing"));
    assertTrue(dump(r), hasFinding(r, CheckStatus.WARNING, "would refuse"));
  }

  @Test
  public void unreachableMxIsPartial() {
    DeliveryCheckResultDto r = run(records(true), serving(GOOD_POLICY.replace("mx: mx1.example.org", "mx: down.example.org")), "down.example.org");
    assertEquals(dump(r), CheckStatus.OK, r.getStatus());
    assertTrue(r.isPartial());
    assertTrue(r.getSummary(), r.getSummary().contains("MX certificates not checked"));
  }

  @Test
  public void policyProblems() {
    assertTrue(hasFinding(run(records(true), domain -> { throw new IOException("PKIX path building failed"); }, "mx1.example.org"),
        CheckStatus.ERROR, "can't fetch the MTA-STS policy"));
    assertTrue(hasFinding(run(records(true), domain -> new MtaStsCheck.PolicyResponse(301, "text/html", ""), "mx1.example.org"),
        CheckStatus.ERROR, "a redirect, which senders don't follow"));
    assertTrue(hasFinding(run(records(true), serving(GOOD_POLICY.replace("version: STSv1\r\n", "")), "mx1.example.org"),
        CheckStatus.ERROR, "has no version"));
    assertTrue(hasFinding(run(records(true), serving(GOOD_POLICY.replace("enforce", "strict")), "mx1.example.org"),
        CheckStatus.ERROR, "invalid mode \"strict\""));
    assertTrue(hasFinding(run(records(true), serving(GOOD_POLICY.replace("604800", "99999999")), "mx1.example.org"),
        CheckStatus.ERROR, "max_age \"99999999\" is invalid"));
    assertTrue(hasFinding(run(records(true), serving(GOOD_POLICY.replace("604800", "3600")), "mx1.example.org"),
        CheckStatus.WARNING, "max_age is only 3600 seconds"));
    assertTrue(hasFinding(run(records(true), domain -> new MtaStsCheck.PolicyResponse(200, "text/html", GOOD_POLICY), "mx1.example.org"),
        CheckStatus.WARNING, "instead of text/plain"));
  }

  @Test
  public void recordProblems() {
    assertTrue(hasFinding(run(new FakeSpfDnsResolver().withTxt("_mta-sts.example.org", "v=STSv1; id=1", "v=STSv1; id=2"), serving(GOOD_POLICY),
        "mx1.example.org"), CheckStatus.ERROR, "2 MTA-STS records"));
    assertTrue(hasFinding(run(new FakeSpfDnsResolver().withTxt("_mta-sts.example.org", "v=STSv1"), serving(GOOD_POLICY), "mx1.example.org"),
        CheckStatus.ERROR, "has no id="));
    assertTrue(hasFinding(run(new FakeSpfDnsResolver().withTxt("_smtp._tls.example.org", "v=TLSRPTv1; rua=smtp://x"), serving(""), "mx1.example.org"),
        CheckStatus.ERROR, "\"smtp://x\" is invalid"));
    assertTrue(hasFinding(run(new FakeSpfDnsResolver().withTxt("_smtp._tls.example.org", "v=TLSRPTv1"), serving(""), "mx1.example.org"),
        CheckStatus.ERROR, "has no rua="));
  }

  @Test
  public void mxPatterns() {
    assertTrue(MtaStsCheck.mxMatches("mail.example.org", "MAIL.example.org"));
    assertTrue(MtaStsCheck.mxMatches("*.example.org", "mx1.example.org"));
    assertFalse(MtaStsCheck.mxMatches("*.example.org", "a.b.example.org"));
    assertFalse(MtaStsCheck.mxMatches("*.example.org", "example.org"));
  }

  @Test
  public void certificateValidation() {
    MtaStsCheck check = new MtaStsCheck(new FakeSpfDnsResolver(), new FakeMx(), serving(""), SERVERS, TRUST_TEST_CA);
    assertNull(check.certificateProblem(List.of(MX, CA), "mx1.example.org"));
    assertNull(check.certificateProblem(List.of(MX, CA), "x.mail.example.org"));
    assertTrue(check.certificateProblem(List.of(MX, CA), "y.x.mail.example.org").contains("isn't valid for the name"));
    assertTrue(check.certificateProblem(List.of(OTHER), "mx1.example.org").contains("isn't trusted"));
  }
}
