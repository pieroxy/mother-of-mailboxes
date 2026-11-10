package net.pieroxy.mom.utils.mail;

import net.pieroxy.mom.config.general.MailAccountConfiguration;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The addresses delivered to an account's mailbox: its configured {@code addresses}, plus its SMTP
 * sender address. An entry is {@code name@domain}, or {@code @domain} for every address of a domain
 * (not its subdomains). Comparisons ignore case.
 */
public final class AccountAddresses {
  public static final AccountAddresses NONE = new AccountAddresses(List.of());

  private final Set<String> addresses = new HashSet<>();
  /** {@code @domain} entries, lower-cased. */
  private final List<String> domains = new ArrayList<>();

  public AccountAddresses(List<String> entries) {
    for (String entry : entries) {
      String lower = entry.trim().toLowerCase(Locale.ROOT);
      if (lower.startsWith("@")) domains.add(lower);
      else addresses.add(lower);
    }
  }

  public static AccountAddresses of(MailAccountConfiguration config) {
    List<String> entries = new ArrayList<>(config.getAddresses() != null ? config.getAddresses() : List.of());
    if (config.getSmtp() != null && config.getSmtp().getFromAddress() != null) entries.add(config.getSmtp().getFromAddress());
    return new AccountAddresses(entries);
  }

  public boolean contains(String address) {
    if (address == null) return false;
    String lower = address.toLowerCase(Locale.ROOT);
    if (addresses.contains(lower)) return true;
    return domains.stream().anyMatch(lower::endsWith);
  }

  /**
   * Trimmed, lower-cased and deduplicated; blank entries are dropped.
   *
   * @throws IllegalArgumentException on an entry that is neither an address nor {@code @domain}.
   */
  public static List<String> normalize(List<String> entries) {
    Set<String> normalized = new LinkedHashSet<>();
    for (String entry : entries != null ? entries : List.<String>of()) {
      String trimmed = entry != null ? entry.trim().toLowerCase(Locale.ROOT) : "";
      if (trimmed.isEmpty()) continue;
      if (trimmed.startsWith("@")) {
        if (!trimmed.matches("@[^@\\s]+\\.[^@\\s]+")) {
          throw new IllegalArgumentException("\"" + entry + "\" is not a valid domain (expected @example.com).");
        }
      } else {
        try {
          if (!trimmed.contains("@")) throw new AddressException();
          new InternetAddress(trimmed, true).validate();
        } catch (AddressException e) {
          throw new IllegalArgumentException("\"" + entry + "\" is not a valid email address.");
        }
      }
      normalized.add(trimmed);
    }
    return new ArrayList<>(normalized);
  }
}
