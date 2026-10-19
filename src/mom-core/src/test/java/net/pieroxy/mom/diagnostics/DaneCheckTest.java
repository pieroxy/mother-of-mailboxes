package net.pieroxy.mom.diagnostics;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DaneCheckTest {
  private static final X509Certificate CERT_1 = load("dane-test-cert-1.pem");
  private static final X509Certificate CERT_2 = load("dane-test-cert-2.pem");

  private static X509Certificate load(String name) {
    try (InputStream in = DaneCheckTest.class.getResourceAsStream("/diagnostics/" + name)) {
      return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** "3 1 1": SHA-256 of the certificate's public key. */
  private static DaneDns.Tlsa daneEe(X509Certificate cert) throws Exception {
    return new DaneDns.Tlsa(3, 1, 1, MessageDigest.getInstance("SHA-256").digest(cert.getPublicKey().getEncoded()));
  }

  private static class FakeDns implements DaneDns {
    Answer<String> mx = new Answer<>(List.of(), Security.SECURE, null);
    final Map<String, Answer<Tlsa>> tlsa = new HashMap<>();

    FakeDns mx(Security security, String... hosts) {
      mx = new Answer<>(List.of(hosts), security, null);
      return this;
    }

    FakeDns tlsa(String host, Security security, Tlsa... records) {
      tlsa.put("_25._tcp." + host, new Answer<>(List.of(records), security, security == Security.BOGUS ? "signature expired" : null));
      return this;
    }

    public Answer<String> mx(String domain) {
      return mx;
    }

    public Answer<Tlsa> tlsa(String name) {
      return tlsa.getOrDefault(name, new Answer<>(List.of(), mx.security(), null));
    }

    public String describe() {
      return "fake";
    }
  }

  /** Each host presents its own certificate; "down.example.org" can't be reached, "plain.example.org" has no STARTTLS. */
  private static final StartTlsProbe.CertificateSource SERVERS = host -> switch (host) {
    case "mx1.example.org" -> List.of(CERT_1);
    case "mx2.example.org" -> List.of(CERT_2);
    case "plain.example.org" -> throw new StartTlsProbe.NoStartTlsException("no STARTTLS");
    default -> throw new IOException("Connect timed out");
  };

  private static DeliveryCheckResultDto run(FakeDns dns) {
    return new DaneCheck(dns, SERVERS).run("example.org");
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
  public void notSetUp() {
    DeliveryCheckResultDto r = run(new FakeDns().mx(DaneDns.Security.INSECURE, "mx1.example.org"));
    assertEquals(dump(r), CheckStatus.INFO, r.getStatus());
    assertEquals("DANE isn't set up for example.org (optional).", r.getSummary());
    assertTrue(dump(r), hasFinding(r, CheckStatus.INFO, "requires DNSSEC first"));
    DeliveryCheckResultDto signed = run(new FakeDns().mx(DaneDns.Security.SECURE, "mx1.example.org"));
    assertTrue(dump(signed), hasFinding(signed, CheckStatus.INFO, "already DNSSEC-signed"));
  }

  @Test
  public void validDane() throws Exception {
    DeliveryCheckResultDto r = run(new FakeDns().mx(DaneDns.Security.SECURE, "mx1.example.org", "mx2.example.org")
        .tlsa("mx1.example.org", DaneDns.Security.SECURE, daneEe(CERT_1))
        .tlsa("mx2.example.org", DaneDns.Security.SECURE, daneEe(CERT_1), daneEe(CERT_2)));
    assertEquals(dump(r), CheckStatus.OK, r.getStatus());
    assertFalse(r.isPartial());
    assertTrue(dump(r), hasFinding(r, CheckStatus.OK, "mx2.example.org presents matches"));
  }

  @Test
  public void fullCertificateAndSha512Records() throws Exception {
    DaneDns.Tlsa full = new DaneDns.Tlsa(3, 0, 0, CERT_1.getEncoded());
    DaneDns.Tlsa sha512 = new DaneDns.Tlsa(3, 0, 2, MessageDigest.getInstance("SHA-512").digest(CERT_1.getEncoded()));
    assertTrue(DaneCheck.matches(full, List.of(CERT_1)));
    assertTrue(DaneCheck.matches(sha512, List.of(CERT_1)));
    assertFalse(DaneCheck.matches(sha512, List.of(CERT_2)));
    // DANE-TA: the trust anchor is in the chain, not the server's own certificate.
    DaneDns.Tlsa ta = new DaneDns.Tlsa(2, 1, 1, MessageDigest.getInstance("SHA-256").digest(CERT_2.getPublicKey().getEncoded()));
    assertTrue(DaneCheck.matches(ta, List.of(CERT_1, CERT_2)));
    assertFalse(DaneCheck.matches(ta, List.of(CERT_2)));
  }

  @Test
  public void certificateNotMatching() throws Exception {
    DeliveryCheckResultDto r = run(new FakeDns().mx(DaneDns.Security.SECURE, "mx1.example.org")
        .tlsa("mx1.example.org", DaneDns.Security.SECURE, daneEe(CERT_2)));
    assertEquals(dump(r), CheckStatus.ERROR, r.getStatus());
    assertTrue(dump(r), hasFinding(r, CheckStatus.ERROR, "matches none of its TLSA records"));
  }

  @Test
  public void unreachableIsPartial() throws Exception {
    DeliveryCheckResultDto r = run(new FakeDns().mx(DaneDns.Security.SECURE, "down.example.org")
        .tlsa("down.example.org", DaneDns.Security.SECURE, daneEe(CERT_1)));
    assertEquals(dump(r), CheckStatus.OK, r.getStatus());
    assertTrue(r.isPartial());
    assertTrue(dump(r), hasFinding(r, CheckStatus.INFO, "Couldn't connect to down.example.org on port 25"));
    assertTrue(r.getSummary(), r.getSummary().contains("certificates not checked"));
  }

  @Test
  public void noStartTls() throws Exception {
    DeliveryCheckResultDto r = run(new FakeDns().mx(DaneDns.Security.SECURE, "plain.example.org")
        .tlsa("plain.example.org", DaneDns.Security.SECURE, daneEe(CERT_1)));
    assertEquals(dump(r), CheckStatus.ERROR, r.getStatus());
    assertTrue(dump(r), hasFinding(r, CheckStatus.ERROR, "doesn't offer STARTTLS"));
  }

  @Test
  public void tlsaWithoutDnssecIsIgnored() throws Exception {
    DeliveryCheckResultDto r = run(new FakeDns().mx(DaneDns.Security.INSECURE, "mx1.example.org")
        .tlsa("mx1.example.org", DaneDns.Security.INSECURE, daneEe(CERT_1)));
    assertEquals(dump(r), CheckStatus.WARNING, r.getStatus());
    assertTrue(dump(r), hasFinding(r, CheckStatus.WARNING, "these are ignored"));
  }

  @Test
  public void bogusDnssec() {
    FakeDns dns = new FakeDns();
    dns.mx = new DaneDns.Answer<>(List.of(), DaneDns.Security.BOGUS, "signature expired");
    DeliveryCheckResultDto r = run(dns);
    assertEquals(dump(r), CheckStatus.ERROR, r.getStatus());
    assertTrue(r.getSummary(), r.getSummary().contains("DNSSEC is broken (signature expired)"));
    DeliveryCheckResultDto host = run(new FakeDns().mx(DaneDns.Security.SECURE, "mx1.example.org").tlsa("mx1.example.org", DaneDns.Security.BOGUS));
    assertTrue(dump(host), hasFinding(host, CheckStatus.ERROR, "The DNSSEC of mx1.example.org is broken"));
  }

  @Test
  public void someHostsWithoutTlsaAndUnusableRecords() throws Exception {
    DeliveryCheckResultDto r = run(new FakeDns().mx(DaneDns.Security.SECURE, "mx1.example.org", "mx2.example.org")
        .tlsa("mx1.example.org", DaneDns.Security.SECURE, new DaneDns.Tlsa(1, 1, 1, new byte[32])));
    assertEquals(dump(r), CheckStatus.ERROR, r.getStatus());
    assertTrue(dump(r), hasFinding(r, CheckStatus.WARNING, "Only some of example.org's MX hosts"));
    assertTrue(dump(r), hasFinding(r, CheckStatus.WARNING, "usage 1 (PKIX-EE)"));
    assertTrue(dump(r), hasFinding(r, CheckStatus.ERROR, "None of mx1.example.org's TLSA records is usable"));
  }
}
