package net.pieroxy.mom.diagnostics;

import net.jpountz.lz4.LZ4FrameOutputStream;
import net.pieroxy.mom.config.general.ReputationListConfig;
import net.pieroxy.mom.detection.reputation.ReputationListType;
import net.pieroxy.mom.detection.reputation.ReputationRegistry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ReputationCheckTest {
  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  /** Lists as {id: content}; ids starting with "allow" score 0, "grey" 0.5, the rest 1. Ids ending with "-missing" have no cache. */
  private ReputationRegistry registry(ReputationListType type, Map<String, String> lists) throws Exception {
    File data = tmp.newFolder();
    File folder = new File(data, "reputation");
    folder.mkdirs();
    List<ReputationListConfig> configs = new ArrayList<>();
    for (Map.Entry<String, String> e : lists.entrySet()) {
      ReputationListConfig cfg = new ReputationListConfig();
      cfg.setId(e.getKey());
      cfg.setType(type);
      cfg.setUrl("https://lists.example/" + e.getKey());
      cfg.setRefreshHours(24);
      cfg.setScore(e.getKey().startsWith("allow") ? 0 : e.getKey().startsWith("grey") ? 0.5 : 1);
      configs.add(cfg);
      if (e.getKey().endsWith("-missing")) continue;
      try (Writer w = new OutputStreamWriter(new LZ4FrameOutputStream(new FileOutputStream(new File(folder, e.getKey() + ".txt.lz4"))),
          StandardCharsets.UTF_8)) {
        w.write(e.getValue());
      }
    }
    return new ReputationRegistry(configs, data.getAbsolutePath());
  }

  /** In-memory A/AAAA records for the SMTP server fallback. */
  private static final MxDnsResolver DNS = new MxDnsResolver() {
    public List<Mx> lookupMx(String domain) {
      return List.of();
    }

    public List<String> lookupCname(String name) {
      return List.of();
    }

    public List<String> lookupA(String name) {
      return name.equals("smtp.provider.net") ? List.of("198.51.100.7") : List.of();
    }

    public List<String> lookupAaaa(String name) {
      return name.equals("smtp.provider.net") ? List.of("2001:db8::7") : List.of();
    }
  };

  private static TestSendOutcome received(String ip, String envelopeFrom, String dkimDomain) {
    String raw = "Return-Path: <" + envelopeFrom + ">\r\n"
        + (dkimDomain == null ? "" : "DKIM-Signature: v=1; a=rsa-sha256;\r\n\td=" + dkimDomain + "; s=sel1; b=abc\r\n")
        + "Subject: x\r\n\r\nbody\r\n";
    return TestSendOutcome.received(new ReceivedTestMessage(raw.getBytes(StandardCharsets.ISO_8859_1), ip, "out.example.org",
        envelopeFrom, null, 1000));
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
  public void ipNotListed() throws Exception {
    ReputationCheck check = new ReputationCheck(registry(ReputationListType.IP_CIDR, Map.of("spamlist", "192.0.2.0/24\n")), DNS);
    DeliveryCheckResultDto r = check.runIp("smtp.provider.net", () -> received("203.0.113.7", "me@example.org", null));
    assertEquals(dump(r), CheckStatus.OK, r.getStatus());
    assertEquals("The address your mail comes from isn't on any of your IP reputation lists.", r.getSummary());
    assertTrue(dump(r), hasFinding(r, CheckStatus.OK, "203.0.113.7 isn't on spamlist"));
  }

  @Test
  public void ipListedWithSeverityFromTheScore() throws Exception {
    ReputationCheck check = new ReputationCheck(registry(ReputationListType.IP_CIDR, Map.of(
        "spamlist", "203.0.113.0/24\n", "greylist", "203.0.113.7\n", "allowlist", "203.0.113.7\n", "other", "192.0.2.1\n")), DNS);
    DeliveryCheckResultDto r = check.runIp(null, () -> received("203.0.113.7", "me@example.org", null));
    assertEquals(dump(r), CheckStatus.ERROR, r.getStatus());
    assertTrue(dump(r), hasFinding(r, CheckStatus.ERROR, "203.0.113.7 is listed on spamlist (score 1.0)"));
    assertTrue(dump(r), hasFinding(r, CheckStatus.WARNING, "listed on greylist (score 0.5)"));
    assertTrue(dump(r), hasFinding(r, CheckStatus.OK, "is on allowlist, a list of trusted senders"));
    assertTrue(dump(r), hasFinding(r, CheckStatus.OK, "isn't on other"));
  }

  @Test
  public void withoutTestMessageFallsBackToTheSmtpServer() throws Exception {
    ReputationCheck check = new ReputationCheck(registry(ReputationListType.IP_CIDR, Map.of("spamlist", "198.51.100.7\n")), DNS);
    DeliveryCheckResultDto r = check.runIp("smtp.provider.net", TestSendOutcome::notConfigured);
    assertEquals(dump(r), CheckStatus.ERROR, r.getStatus());
    assertTrue(r.isPartial());
    assertTrue(dump(r), hasFinding(r, CheckStatus.INFO, "2001:db8::7 is an IPv6 address"));
    assertTrue(dump(r), hasFinding(r, CheckStatus.ERROR, "198.51.100.7 is listed on spamlist"));
  }

  @Test
  public void noListsOrNotDownloadedYet() throws Exception {
    ReputationCheck none = new ReputationCheck(registry(ReputationListType.DOMAIN, Map.of("d", "x.org\n")), DNS);
    assertEquals(CheckStatus.SKIPPED, none.runIp(null, TestSendOutcome::notConfigured).getStatus());
    ReputationCheck missing = new ReputationCheck(registry(ReputationListType.IP_CIDR, Map.of("spam-missing", "")), DNS);
    DeliveryCheckResultDto r = missing.runIp(null, () -> received("203.0.113.7", "me@example.org", null));
    assertTrue(r.isPartial());
    assertTrue(dump(r), hasFinding(r, CheckStatus.INFO, "Not checked against spam-missing: never downloaded yet"));
  }

  @Test
  public void domainsFromTheTestMessageAndOrganizationalDomains() throws Exception {
    ReputationCheck check = new ReputationCheck(registry(ReputationListType.DOMAIN, Map.of(
        "baddomains", "provider.net\n", "greydomains", "example.org\n")), DNS);
    DeliveryCheckResultDto r = check.runDomain("mail.example.org", () -> received("203.0.113.7", "b-1@bounces.provider.net", "signer.example"));
    assertEquals(dump(r), CheckStatus.ERROR, r.getStatus());
    assertTrue(dump(r), r.getFacts().stream().anyMatch(f -> f.getValue()
        .equals("mail.example.org, bounces.provider.net, signer.example, example.org, provider.net")));
    assertTrue(dump(r), hasFinding(r, CheckStatus.ERROR, "provider.net is listed on baddomains"));
    assertTrue(dump(r), hasFinding(r, CheckStatus.WARNING, "example.org is listed on greydomains"));
  }

  @Test
  public void domainWithoutTestMessageIsPartial() throws Exception {
    ReputationCheck check = new ReputationCheck(registry(ReputationListType.DOMAIN, Map.of("baddomains", "spam.example\n")), DNS);
    DeliveryCheckResultDto r = check.runDomain("example.org", TestSendOutcome::notConfigured);
    assertEquals(dump(r), CheckStatus.OK, r.getStatus());
    assertTrue(r.isPartial());
    assertEquals(CheckStatus.IN_PROGRESS, check.runDomain("example.org", () -> TestSendOutcome.pending("…")).getStatus());
  }

  @Test
  public void neighborhood() throws Exception {
    StringBuilder crowded = new StringBuilder();
    for (int i = 100; i < 130; i++) crowded.append("203.0.113.").append(i).append('\n');
    ReputationCheck check = new ReputationCheck(registry(ReputationListType.IP_CIDR, Map.of(
        "crowded", crowded.toString(), "sparse", "203.0.113.200\n", "elsewhere", "192.0.2.0/24\n", "allowlist", "203.0.113.0/24\n")), DNS);
    DeliveryCheckResultDto r = check.runIp(null, () -> received("203.0.113.7", "me@example.org", null));
    assertEquals(dump(r), CheckStatus.WARNING, r.getStatus());
    assertTrue(dump(r), hasFinding(r, CheckStatus.WARNING, "30 other addresses of 203.0.113.0/24 are on crowded"));
    assertTrue(dump(r), hasFinding(r, CheckStatus.INFO, "1 other address of 203.0.113.0/24 is on sparse"));
    assertTrue(dump(r), hasFinding(r, CheckStatus.OK, "No other address of 203.0.113.0/24 is on elsewhere"));
    assertTrue("trusted lists aren't neighborhood-checked: " + dump(r), r.getFindings().stream()
        .noneMatch(f -> f.getMessage().contains("of 203.0.113.0/24 are on allowlist")));
  }

  @Test
  public void neighborhoodNotRepeatedWhenListedItself() throws Exception {
    ReputationCheck check = new ReputationCheck(registry(ReputationListType.IP_CIDR, Map.of("spamlist", "203.0.113.0/24\n")), DNS);
    DeliveryCheckResultDto r = check.runIp(null, () -> received("203.0.113.7", "me@example.org", null));
    assertTrue(dump(r), hasFinding(r, CheckStatus.ERROR, "203.0.113.7 is listed on spamlist"));
    assertTrue(dump(r), r.getFindings().stream().noneMatch(f -> f.getMessage().contains("other address")));
  }
}
