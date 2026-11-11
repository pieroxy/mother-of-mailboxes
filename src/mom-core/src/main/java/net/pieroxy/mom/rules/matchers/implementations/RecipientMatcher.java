package net.pieroxy.mom.rules.matchers.implementations;

import net.pieroxy.mom.config.general.MailFilterRuleMatcherConfiguration;
import net.pieroxy.mom.rules.RuleContext;
import net.pieroxy.mom.rules.matchers.MatchResult;
import net.pieroxy.mom.rules.matchers.Matcher;
import net.pieroxy.mom.utils.mail.AccountAddresses;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Base of the RECIPIENT_* matchers: matches when the value {@link #valueOf} derives from any
 * recipient equals a key, case-insensitively. Recipients are the {@code To} and {@code Cc}
 * addresses, plus those of the account's envelope headers. Only the topmost occurrence of an
 * envelope header is read: servers prepend headers, so the others come from earlier hops, or from
 * the sender.
 * <p>
 * Learning keeps the recipients that are the account's own addresses (all of them if it has
 * none), and needs them to give exactly one value.
 */
public abstract class RecipientMatcher extends Matcher {
  private final String typeName;
  private List<String> envelopeHeaders = List.of();
  private AccountAddresses accountAddresses = AccountAddresses.NONE;

  protected RecipientMatcher(String typeName) {
    this.typeName = typeName;
  }

  /** The value compared to the keys for this lower-cased {@code address}; null if it has none. */
  protected abstract String valueOf(String address);

  @Override
  protected void bindContext(RuleContext context) {
    envelopeHeaders = context.envelopeHeaders();
    accountAddresses = context.accountAddresses();
  }

  @Override
  public void setConfig(MailFilterRuleMatcherConfiguration config) {
    super.setConfig(config);
    initLookupSet(true);
  }

  @Override
  public MatchResult matches(Message message) throws MessagingException {
    Set<String> recipients = recipients(message);
    for (String recipient : recipients) {
      Optional<String> hit = matchesKey(valueOf(recipient), true);
      if (hit.isPresent()) {
        getLogger().fine(() -> "recipient " + recipient + " matches " + describeKey());
        return matched(hit.get());
      }
    }
    getLogger().fine(() -> "recipients " + recipients + ", no match against " + describeKey());
    return notMatched();
  }

  @Override
  public String extractKeyFromExample(Message message) throws MessagingException {
    Set<String> recipients = recipients(message);
    Set<String> candidates = accountAddresses.isEmpty() ? recipients
        : recipients.stream().filter(accountAddresses::contains).collect(Collectors.toCollection(LinkedHashSet::new));
    Set<String> values = new LinkedHashSet<>();
    for (String candidate : candidates) {
      String value = valueOf(candidate);
      if (value != null) values.add(value);
    }
    if (values.size() == 1) return values.iterator().next();
    String among = accountAddresses.isEmpty() ? "recipients " : "of the account's addresses among recipients ";
    throw new MessagingException("Cannot learn a " + typeName + " rule: " + (values.isEmpty() ? "none " : "several ") + among + recipients);
  }

  /** Lower-cased, envelope headers first. */
  private Set<String> recipients(Message message) throws MessagingException {
    Set<String> recipients = new LinkedHashSet<>();
    for (String header : envelopeHeaders) {
      String[] values = message.getHeader(header);
      if (values == null || values.length == 0) continue;
      try {
        addAll(recipients, InternetAddress.parseHeader(values[0], false));
      } catch (AddressException e) {
        getLogger().fine(() -> "unparseable " + header + " header: " + values[0]);
      }
    }
    for (Message.RecipientType type : List.of(Message.RecipientType.TO, Message.RecipientType.CC)) {
      try {
        addAll(recipients, message.getRecipients(type));
      } catch (AddressException e) {
        getLogger().fine(() -> "unparseable " + type + " header: " + e.getMessage());
      }
    }
    return recipients;
  }

  private static void addAll(Set<String> recipients, Address[] addresses) {
    if (addresses == null) return;
    for (Address address : addresses) {
      if (address instanceof InternetAddress internetAddress && internetAddress.getAddress() != null) {
        recipients.add(internetAddress.getAddress().toLowerCase(Locale.ROOT));
      }
    }
  }

  /** The part after the last {@code @}; null without one, or with nothing on either side of it. */
  static String domainOf(String address) {
    int at = address.lastIndexOf('@');
    return at > 0 && at < address.length() - 1 ? address.substring(at + 1) : null;
  }
}
