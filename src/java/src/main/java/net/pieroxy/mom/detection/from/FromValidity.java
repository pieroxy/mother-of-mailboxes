package net.pieroxy.mom.detection.from;

import net.pieroxy.mom.api.metadata.TypeScriptNonConstEnum;
import net.pieroxy.mom.api.metadata.TypeScriptType;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeUtility;
import java.net.IDN;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
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
      for (InternetAddress address : InternetAddress.parseHeader(redecodeUtf8(MimeUtility.unfold(nonBlank.get(0))), false)) {
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

  /**
   * javax.mail decodes raw header bytes as ISO-8859-1 (unless mail.mime.allowutf8 is set), so a
   * UTF-8 address (RFC 6532) arrives as mojibake. Returns the UTF-8 reading when the bytes are
   * valid UTF-8, the header unchanged otherwise.
   */
  private static String redecodeUtf8(String header) {
    boolean hasHighByte = false;
    for (int i = 0; i < header.length(); i++) {
      char c = header.charAt(i);
      if (c > 0xFF) return header;
      if (c > 0x7F) hasHighByte = true;
    }
    if (!hasHighByte) return header;
    try {
      return StandardCharsets.UTF_8.newDecoder()
          .decode(ByteBuffer.wrap(header.getBytes(StandardCharsets.ISO_8859_1))).toString();
    } catch (CharacterCodingException e) {
      return header;
    }
  }

  /**
   * InternetAddress#validate() rejects any non-ASCII character, so an internationalized address
   * (RFC 6531) is checked on an ASCII stand-in: the domain converted to punycode, and each
   * non-ASCII character of the local part, which must be a letter, digit or combining mark,
   * replaced by an ASCII letter.
   */
  private static boolean isValid(InternetAddress address) {
    String raw = address.getAddress();
    if (raw == null) return false;
    int at = raw.lastIndexOf('@');
    if (at <= 0 || at == raw.length() - 1) return false;
    if (raw.chars().allMatch(c -> c < 0x80)) return validates(address);

    String domain;
    try {
      domain = IDN.toASCII(raw.substring(at + 1), IDN.USE_STD3_ASCII_RULES);
    } catch (IllegalArgumentException e) {
      return false;
    }
    StringBuilder local = new StringBuilder();
    for (int cp : raw.substring(0, at).codePoints().toArray()) {
      if (cp < 0x80) {
        local.appendCodePoint(cp);
      } else if (Character.isLetterOrDigit(cp) || isMark(cp)) {
        local.append('a');
      } else {
        return false;
      }
    }
    try {
      return validates(new InternetAddress(local + "@" + domain, true));
    } catch (AddressException e) {
      return false;
    }
  }

  private static boolean isMark(int cp) {
    int type = Character.getType(cp);
    return type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK || type == Character.ENCLOSING_MARK;
  }

  private static boolean validates(InternetAddress address) {
    try {
      address.validate();
      return true;
    } catch (AddressException e) {
      return false;
    }
  }
}
