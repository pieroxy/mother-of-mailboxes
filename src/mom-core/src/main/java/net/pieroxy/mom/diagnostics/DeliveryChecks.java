package net.pieroxy.mom.diagnostics;

import net.pieroxy.mom.config.general.SmtpConfiguration;
import net.pieroxy.mom.detection.dkim.DkimVerifier;
import net.pieroxy.mom.detection.dmarc.DnsJavaDmarcDnsResolver;
import net.pieroxy.mom.detection.fcrdns.DnsJavaFcrdnsDnsResolver;
import net.pieroxy.mom.detection.spf.DnsJavaSpfDnsResolver;
import net.pieroxy.mom.rules.MailAccount;
import org.apache.james.jdkim.impl.DNSPublicKeyRecordRetriever;
import org.xbill.DNS.SimpleResolver;

import java.io.IOException;
import java.time.Duration;
import java.util.function.Supplier;

import java.util.Locale;

/**
 * Runs one {@link DeliveryCheck} against an account. Every check is about the account's sending
 * domain: the domain of its SMTP settings' From address — the one receiving servers judge.
 */
public final class DeliveryChecks {
  private DeliveryChecks() {}

  /**
   * @param sinkUrl the configured SMTP sink, or null if there's none (no test send).
   * @param runId identifies the diagnostics run, so all its checks share one test send (see {@link TestSends}).
   */
  public static DeliveryCheckResultDto run(DeliveryCheck check, MailAccount account, String sinkUrl, String runId) {
    SmtpConfiguration smtp = account.getConfig().getSmtp();
    if (smtp == null) {
      return DeliveryCheckResultDto.skipped(check, "This account has no SMTP settings: there's no sending domain to check.");
    }
    String domain = domainOf(smtp.getFromAddress());
    if (domain == null) {
      return DeliveryCheckResultDto.skipped(check, "The SMTP settings have no valid From address: there's no sending domain to check.");
    }
    Supplier<TestSendOutcome> testSend = () -> sinkUrl == null || account.getMailSender() == null
        ? TestSendOutcome.notConfigured()
        : TestSends.await(account.getAccountLabel(), runId, account.getMailSender(), sinkUrl);
    return switch (check) {
      case SPF -> new SpfCheck(new DnsJavaSpfDnsResolver()).run(domain, smtp.getHost(), testSend);
      case DKIM -> new DkimCheck(dkimKeyRetriever()).run(domain, testSend);
      case DMARC -> new DmarcCheck(new DnsJavaDmarcDnsResolver(), new DnsJavaSpfDnsResolver(), DkimVerifier.createDefault()).run(domain, testSend);
      case REVERSE_DNS -> new ReverseDnsCheck(new DnsJavaFcrdnsDnsResolver()).run(testSend);
      case MX -> new MxCheck(new DnsJavaMxDnsResolver()).run(domain);
    };
  }

  private static DNSPublicKeyRecordRetriever dkimKeyRetriever() {
    try {
      SimpleResolver resolver = new SimpleResolver();
      resolver.setTimeout(Duration.ofSeconds(5));
      return new DNSPublicKeyRecordRetriever(resolver);
    } catch (IOException e) {
      throw new IllegalStateException("Could not initialize the DNS resolver for DKIM", e);
    }
  }

  /** The domain of an address such as "me@example.org" or "Me &lt;me@example.org&gt;", lowercased, or null. */
  static String domainOf(String address) {
    if (address == null) return null;
    String a = address.trim();
    int lt = a.lastIndexOf('<');
    if (lt >= 0) {
      int gt = a.indexOf('>', lt);
      a = gt > lt ? a.substring(lt + 1, gt) : a.substring(lt + 1);
    }
    int at = a.lastIndexOf('@');
    if (at < 0 || at == a.length() - 1) return null;
    String domain = a.substring(at + 1).trim().toLowerCase(Locale.ROOT);
    return domain.isEmpty() || domain.contains(" ") ? null : domain;
  }
}
