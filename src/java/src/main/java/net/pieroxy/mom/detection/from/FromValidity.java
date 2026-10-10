package net.pieroxy.mom.detection.from;

import net.pieroxy.mom.api.metadata.TypeScriptNonConstEnum;
import net.pieroxy.mom.api.metadata.TypeScriptType;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeUtility;
import java.util.ArrayList;
import java.util.List;

/**
 * Structural soundness of a message's {@code From} header (RFC 5322 §3.6: exactly one
 * {@code From} field). {@code @TypeScriptNonConstEnum} so the FROM_VALIDITY_EQUALS value picker
 * can list these via {@code Object.values(FromValidity)}.
 */
@TypeScriptType
@TypeScriptNonConstEnum
public enum FromValidity {
  /** Exactly one {@code From} field, holding exactly one syntactically valid address. */
  VALID,
  /** No {@code From} field, or only blank ones. */
  MISSING,
  /** Several {@code From} fields, or one field listing several mailboxes. */
  MULTIPLE,
  /** One {@code From} field from which no syntactically valid address can be extracted. */
  INVALID;

  public String getCode() {
    return name().toLowerCase();
  }

  /**
   * Reads the raw header rather than {@link Message#getFrom()}: for a MimeMessage, the latter
   * falls back to {@code Sender} when {@code From} is absent, and parses leniently enough to
   * turn garbage into an address.
   */
  public static FromValidity of(Message message) throws MessagingException {
    String[] headers = message.getHeader("From");
    List<String> nonBlank = new ArrayList<>();
    if (headers != null) {
      for (String header : headers) {
        if (header != null && !header.isBlank()) nonBlank.add(header);
      }
    }
    if (nonBlank.isEmpty()) return MISSING;
    if (nonBlank.size() > 1) return MULTIPLE;

    List<InternetAddress> mailboxes = new ArrayList<>();
    try {
      for (InternetAddress address : InternetAddress.parseHeader(MimeUtility.unfold(nonBlank.get(0)), false)) {
        if (address.isGroup()) {
          InternetAddress[] members = address.getGroup(false);
          if (members != null) mailboxes.addAll(List.of(members));
        } else {
          mailboxes.add(address);
        }
      }
    } catch (AddressException e) {
      return INVALID;
    }
    if (mailboxes.isEmpty()) return INVALID;
    if (mailboxes.size() > 1) return MULTIPLE;
    return isValid(mailboxes.get(0)) ? VALID : INVALID;
  }

  private static boolean isValid(InternetAddress address) {
    String raw = address.getAddress();
    if (raw == null || raw.indexOf('@') <= 0) return false;
    try {
      address.validate();
      return true;
    } catch (AddressException e) {
      return false;
    }
  }
}
