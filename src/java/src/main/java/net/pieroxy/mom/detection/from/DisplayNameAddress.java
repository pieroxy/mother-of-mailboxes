package net.pieroxy.mom.detection.from;

import net.pieroxy.mom.api.metadata.TypeScriptNonConstEnum;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.detection.dmarc.DmarcEvaluator;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import java.net.IDN;
import java.text.Normalizer;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How an email address shown in the {@code From} display name relates to the actual address,
 * e.g. {@code "service@paypal.com" <x@evil.ru>}: DMARC only checks the actual address, while
 * many clients show only the display name. {@code @TypeScriptNonConstEnum} so the
 * DISPLAY_NAME_ADDRESS_EQUALS value picker can list these via
 * {@code Object.values(DisplayNameAddress)}.
 */
@TypeScriptType
@TypeScriptNonConstEnum
public enum DisplayNameAddress {
  /** No address in the display name, or {@code From} isn't {@link FromValidity#VALID}. */
  NONE,
  /** Only the actual address, case-insensitively. */
  SAME,
  /** Another address, on the same organizational domain as the actual one. */
  SAME_DOMAIN,
  /** An address on another organizational domain. */
  OTHER_DOMAIN;

  /** A dotted domain is required, so that "John @ Acme" or "@handle" don't count as addresses. */
  private static final Pattern EMBEDDED_ADDRESS =
      Pattern.compile("[\\p{L}\\p{M}\\p{N}._%+\\-]+@[\\p{L}\\p{M}\\p{N}\\-]+(?:\\.[\\p{L}\\p{M}\\p{N}\\-]+)+");

  public String getCode() {
    return name().toLowerCase();
  }

  /**
   * The display name is NFKC-normalized first, so that look-alikes such as a fullwidth
   * {@code ＠} still count. With several addresses in the display name, the worst one wins.
   */
  public static DisplayNameAddress of(Message message) throws MessagingException {
    if (FromValidity.of(message) != FromValidity.VALID) return NONE;
    InternetAddress from;
    try {
      from = FromValidity.mailboxes(message.getHeader("From")[0]).get(0);
    } catch (AddressException e) {
      return NONE;
    }
    String personal = from.getPersonal();
    if (personal == null) return NONE;

    String actual = from.getAddress();
    DisplayNameAddress result = NONE;
    Matcher m = EMBEDDED_ADDRESS.matcher(Normalizer.normalize(personal, Normalizer.Form.NFKC));
    while (m.find()) {
      DisplayNameAddress found = compare(m.group(), actual);
      if (found.ordinal() > result.ordinal()) result = found;
    }
    return result;
  }

  private static DisplayNameAddress compare(String shown, String actual) {
    if (shown.equalsIgnoreCase(actual)) return SAME;
    String shownOrg = organizationalDomain(shown.substring(shown.lastIndexOf('@') + 1));
    String actualOrg = organizationalDomain(actual.substring(actual.lastIndexOf('@') + 1));
    return shownOrg.equals(actualOrg) ? SAME_DOMAIN : OTHER_DOMAIN;
  }

  private static String organizationalDomain(String domain) {
    String ascii;
    try {
      ascii = IDN.toASCII(domain).toLowerCase();
    } catch (IllegalArgumentException e) {
      ascii = domain.toLowerCase();
    }
    return DmarcEvaluator.organizationalDomainOf(ascii).orElse(ascii);
  }
}
