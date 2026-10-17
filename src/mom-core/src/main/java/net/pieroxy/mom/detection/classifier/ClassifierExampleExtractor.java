package net.pieroxy.mom.detection.classifier;

import com.sun.mail.imap.IMAPMessage;
import net.pieroxy.mom.utils.MailTools;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds a {@link ClassifierExample} from a message: mostly headers
 * (Subject/From/To/Date/Received/In-Reply-To/References/Precedence/List-Id/List-Unsubscribe/
 * Return-Path/Reply-To) plus the MIME structure's attachment filenames, and the body's visible
 * text (see {@link #extractBodyText}) — read via {@code Part.getContent()}/{@code Part.getFileName()}
 * on whichever MIME part is picked, so unlike the rest of this class it isn't covered by the
 * batched BODYSTRUCTURE/HEADERS prefetch (see {@code ImapMailboxConnection#getMessagesSince}):
 * one extra IMAP round trip per message, for exactly the one part actually needed — never the
 * whole message, never an attachment's bytes. javax.mail's IMAP provider doesn't reliably honor
 * the session-wide "mail.imap.peek" property for this kind of read (same pitfall as
 * {@link MailTools#readRawMessageWithoutMarkingSeen}), so {@link #extractBodyText} sets
 * {@code IMAPMessage#setPeek(true)} on the message itself first — without it, this would mark
 * every scanned message \Seen.
 */
public final class ClassifierExampleExtractor {
  private static final Pattern IPV4_PATTERN = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");
  private static final String NO_EXTENSION = "(none)";

  private ClassifierExampleExtractor() {}

  public static ClassifierExample extract(Message message, ClassifierLabel label, Instant fetchDate) throws MessagingException {
    ClassifierExample example = new ClassifierExample();
    example.setMessageId(headerValue(message, "Message-ID"));
    example.setFetchDate(DateTimeFormatter.ISO_INSTANT.format(fetchDate));
    example.setMailDate(message.getSentDate() != null
        ? DateTimeFormatter.ISO_INSTANT.format(message.getSentDate().toInstant())
        : null);
    example.setReceivedDate(message.getReceivedDate() != null
        ? DateTimeFormatter.ISO_INSTANT.format(message.getReceivedDate().toInstant())
        : null);
    Address[] from = message.getFrom();
    Address[] to = message.getRecipients(Message.RecipientType.TO);
    example.setFrom(addresses(from));
    example.setFromDisplayName(displayNames(from));
    example.setTo(addresses(to));
    example.setToDisplayName(displayNames(to));
    example.setSubject(message.getSubject());
    example.setIp(extractOriginatingIp(message));

    example.setReply(headerPresent(message, "In-Reply-To") || headerPresent(message, "References"));
    example.setPrecedence(headerValue(message, "Precedence"));
    example.setListId(headerValue(message, "List-Id"));
    example.setListUnsubscribePresent(headerPresent(message, "List-Unsubscribe"));

    String fromDomain = singleFromDomain(from);
    String returnPathDomain = returnPathDomain(message);
    String replyToDomain = replyToDomain(message);
    example.setReturnPathDomain(returnPathDomain);
    example.setReturnPathMismatch(mismatch(fromDomain, returnPathDomain));
    example.setReplyToDomain(replyToDomain);
    example.setReplyToMismatch(mismatch(fromDomain, replyToDomain));

    List<String> attachmentExtensions = new ArrayList<>();
    collectAttachmentExtensions(message, attachmentExtensions);
    example.setAttachmentExtensions(attachmentExtensions);

    extractBody(message, example);

    example.setLabel(label);
    return example;
  }

  /**
   * The body's visible text: the first {@code text/html} part found anywhere in the MIME tree,
   * stripped down to its text (see {@link #stripHtml}) — that's what a human reading the message
   * actually sees, unlike a {@code text/plain} alternative, which in practice is rarely more than
   * an unread fallback for a personal mailbox. Only falls back to a {@code text/plain} part,
   * used as-is, when no HTML part exists at all. Both {@link ClassifierExample#getBodyText()} and
   * {@link ClassifierExample#getBodySource()} are left null if the message has neither (e.g. an
   * image-only or attachment-only message).
   */
  private static void extractBody(Part part, ClassifierExample example) {
    // Same pitfall as MailTools#readRawMessageWithoutMarkingSeen: javax.mail's IMAP provider
    // doesn't reliably honor the "mail.imap.peek" session property for a body part's content
    // (verified empirically — same as writeTo()), only IMAPMessage#setPeek set on this specific
    // message right before reading it.
    if (part instanceof IMAPMessage) {
      ((IMAPMessage) part).setPeek(true);
    }
    BodyCandidate candidate = new BodyCandidate();
    collectBodyCandidate(part, candidate);
    if (candidate.html != null) {
      example.setBodyText(stripHtml(candidate.html));
      example.setBodySource("html");
    } else if (candidate.plain != null) {
      example.setBodyText(candidate.plain);
      example.setBodySource("plain");
    }
  }

  private static void collectBodyCandidate(Part part, BodyCandidate candidate) {
    if (candidate.html != null) return; // already found the preferred kind, nothing left to look for
    try {
      if (part.isMimeType("multipart/*")) {
        Object content = part.getContent();
        if (content instanceof Multipart multipart) {
          for (int i = 0; i < multipart.getCount() && candidate.html == null; i++) {
            collectBodyCandidate(multipart.getBodyPart(i), candidate);
          }
        }
        return;
      }
      // A filename means this is an attachment or an inline file (an embedded image, say), not
      // body text — same signal collectAttachmentExtensions uses.
      String filename = part.getFileName();
      if (filename != null && !filename.isBlank()) return;

      if (candidate.html == null && part.isMimeType("text/html")) {
        Object content = part.getContent();
        if (content instanceof String s) candidate.html = s;
      } else if (candidate.plain == null && part.isMimeType("text/plain")) {
        Object content = part.getContent();
        if (content instanceof String s) candidate.plain = s;
      }
    } catch (MessagingException | IOException e) {
      // Malformed/undecodable part: no body text recoverable from it, not worth failing the
      // whole example extraction over — same tolerance as collectAttachmentExtensions.
    }
  }

  /** Visible text only: strips tags/attributes, and script/style content along with them, collapsing whitespace. */
  private static String stripHtml(String html) {
    Document doc = Jsoup.parse(html);
    doc.select("script, style").remove();
    return doc.text();
  }

  private static final class BodyCandidate {
    String html;
    String plain;
  }

  private static List<String> addresses(Address[] addresses) throws MessagingException {
    List<String> result = new ArrayList<>();
    if (addresses == null) return result;
    for (Address a : addresses) {
      result.add(MailTools.getMailAddress(a));
    }
    return result;
  }

  /**
   * Display name(s) ("Alice" for "Alice &lt;alice@example.com&gt;"), joined by a space —
   * {@link MailTools#getMailAddress} ignores them, even though they're a potentially useful
   * signal for a future classifier (see {@link ClassifierExample#getFromDisplayName()}).
   * @return null if no address has a display name set.
   */
  private static String displayNames(Address[] addresses) {
    if (addresses == null) return null;
    List<String> names = new ArrayList<>();
    for (Address a : addresses) {
      if (a instanceof InternetAddress) {
        String personal = ((InternetAddress) a).getPersonal();
        if (personal != null && !personal.isBlank()) {
          names.add(personal);
        }
      }
    }
    return names.isEmpty() ? null : String.join(" ", names);
  }

  /**
   * Best-effort: takes the first IPv4 address found in the last (oldest) Received header (each
   * successive relay prepends its own, so the last one is closest to the original sender). No
   * full RFC 5321 parsing, no IPv6 for now — to be refined based on what actually turns out to be
   * useful once there's real data.
   */
  private static String extractOriginatingIp(Message message) throws MessagingException {
    String[] received = message.getHeader("Received");
    if (received == null || received.length == 0) return null;
    Matcher m = IPV4_PATTERN.matcher(received[received.length - 1]);
    return m.find() ? m.group() : null;
  }

  /**
   * Recursively walks the MIME tree (fetched as BODYSTRUCTURE, not the actual body bytes — see
   * ImapMailboxConnection's FetchProfile.Item.CONTENT_INFO for the corpus scan's batched
   * prefetch of this) collecting one lowercase extension per attachment-like part found — any
   * part carrying a filename, not just ones marked Content-Disposition: attachment, since some
   * mailers omit that but still name the part.
   */
  private static void collectAttachmentExtensions(Part part, List<String> extensionsOut) throws MessagingException {
    if (!part.isMimeType("multipart/*")) {
      String filename = part.getFileName();
      if (filename != null && !filename.isBlank()) {
        extensionsOut.add(extensionOf(filename));
      }
      return;
    }
    try {
      Object content = part.getContent();
      if (content instanceof Multipart multipart) {
        for (int i = 0; i < multipart.getCount(); i++) {
          collectAttachmentExtensions(multipart.getBodyPart(i), extensionsOut);
        }
      }
    } catch (IOException e) {
      // Malformed/undecodable part: no attachment info recoverable from it, not worth failing
      // the whole example extraction over.
    }
  }

  private static String extensionOf(String filename) {
    int dot = filename.lastIndexOf('.');
    return dot >= 0 && dot < filename.length() - 1 ? filename.substring(dot + 1).toLowerCase(Locale.ROOT) : NO_EXTENSION;
  }

  private static boolean headerPresent(Message message, String name) throws MessagingException {
    String[] values = message.getHeader(name);
    return values != null && values.length > 0;
  }

  private static String headerValue(Message message, String name) throws MessagingException {
    String[] values = message.getHeader(name);
    if (values == null || values.length == 0) return null;
    String value = values[0].trim();
    return value.isEmpty() ? null : value;
  }

  /** Domain of From, only if the message has exactly one From address (same convention as FromDomainMatcher/SpfIdentityExtractor). */
  private static String singleFromDomain(Address[] from) {
    if (from == null || from.length != 1) return null;
    String raw = from[0] instanceof InternetAddress ? ((InternetAddress) from[0]).getAddress() : from[0].toString();
    return domainOf(raw);
  }

  /** Return-Path is in the "&lt;addr&gt;" format (sometimes "&lt;&gt;" for a bounce with no return address) — not a normal RFC 5322 address, never parsed as one (see SpfIdentityExtractor). */
  private static String returnPathDomain(Message message) throws MessagingException {
    String value = headerValue(message, "Return-Path");
    if (value == null) return null;
    if (value.startsWith("<") && value.endsWith(">")) {
      value = value.substring(1, value.length() - 1);
    }
    return domainOf(value);
  }

  private static String replyToDomain(Message message) throws MessagingException {
    String value = headerValue(message, "Reply-To");
    if (value == null) return null;
    try {
      InternetAddress[] addresses = InternetAddress.parse(value, false);
      return addresses.length == 0 ? null : domainOf(addresses[0].getAddress());
    } catch (AddressException e) {
      return null;
    }
  }

  private static String domainOf(String address) {
    if (address == null) return null;
    int at = address.lastIndexOf('@');
    if (at < 0 || at >= address.length() - 1) return null;
    return address.substring(at + 1);
  }

  /** null (undeterminable) if either domain is missing — never confused with "no mismatch". */
  private static Boolean mismatch(String fromDomain, String otherDomain) {
    if (fromDomain == null || otherDomain == null) return null;
    return !fromDomain.equalsIgnoreCase(otherDomain);
  }
}
