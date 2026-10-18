package net.pieroxy.mom.diagnostics;

import net.pieroxy.mom.config.general.MailAccountConfiguration;
import net.pieroxy.mom.config.general.SmtpConfiguration;
import net.pieroxy.mom.detection.spf.DnsJavaSpfDnsResolver;

import java.util.Locale;

/**
 * Runs one {@link DeliveryCheck} against an account. Every check is about the account's sending
 * domain: the domain of its SMTP settings' From address — the one receiving servers judge.
 */
public final class DeliveryChecks {
  private DeliveryChecks() {}

  public static DeliveryCheckResultDto run(DeliveryCheck check, MailAccountConfiguration config) {
    SmtpConfiguration smtp = config.getSmtp();
    if (smtp == null) {
      return DeliveryCheckResultDto.skipped(check, "This account has no SMTP settings: there's no sending domain to check.");
    }
    String domain = domainOf(smtp.getFromAddress());
    if (domain == null) {
      return DeliveryCheckResultDto.skipped(check, "The SMTP settings have no valid From address: there's no sending domain to check.");
    }
    return switch (check) {
      case SPF -> new SpfCheck(new DnsJavaSpfDnsResolver()).run(domain, smtp.getHost());
    };
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
