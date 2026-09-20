package net.pieroxy.imf.detection.spf;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Logger;

/**
 * Extracts, from the headers of an already-received message, the two pieces of information
 * {@link SpfEvaluator} needs: the IP that connected over SMTP, and the sender domain to check.
 * <p>
 * Our IMAP server doesn't perform SPF verification itself (unlike a webmail provider such as
 * Gmail, which writes it into {@code Authentication-Results}): we redo it ourselves, from
 * whatever the MTA that received the message wrote into the most recent {@code Received}
 * header — the one added by our own server, which reflects the real TCP connection and is
 * therefore the only trustworthy source (older {@code Received} headers may have been forged
 * by the sender).
 */
public class SpfIdentityExtractor {
  // Captures the last "[ip]" (IPv4, or IPv6 with optional "IPv6:" prefix) in the "from" clause
  // of a Received header, e.g. "from host.example.com (host.example.com [203.0.113.9])".
  private static final Pattern BRACKETED_IP = Pattern.compile("\\[(?:IPv6:)?([0-9a-fA-F:.]+)]", Pattern.CASE_INSENSITIVE);
  private static final Pattern BY_CLAUSE = Pattern.compile("\\sby\\s", Pattern.CASE_INSENSITIVE);
  private static final Logger DEFAULT_LOGGER = Logger.getLogger(SpfIdentityExtractor.class.getName());

  private SpfIdentityExtractor() {
  }

  /** The IP that connected to send this message, read from the most recent Received header. */
  public static Optional<String> extractClientIp(Message message) throws MessagingException {
    return extractClientIp(message, DEFAULT_LOGGER);
  }

  /** Like {@link #extractClientIp(Message)}, but logs (FINE level) the examined header and the IP found. */
  public static Optional<String> extractClientIp(Message message, Logger logger) throws MessagingException {
    String[] received = message.getHeader("Received");
    if (received == null || received.length == 0) {
      logger.fine(() -> "No Received header on message, cannot determine the connecting IP");
      return Optional.empty();
    }

    String normalized = received[0].replaceAll("\\s+", " ").trim();
    logger.fine(() -> "Most recent Received header: " + normalized);
    Matcher byMatcher = BY_CLAUSE.matcher(normalized);
    String fromClause = byMatcher.find() ? normalized.substring(0, byMatcher.start()) : normalized;

    String lastIp = null;
    Matcher ipMatcher = BRACKETED_IP.matcher(fromClause);
    while (ipMatcher.find()) {
      lastIp = ipMatcher.group(1);
    }
    String foundIp = lastIp;
    if (foundIp != null) {
      logger.fine(() -> "Extracted connecting IP: " + foundIp);
    } else {
      logger.fine(() -> "Could not find a bracketed IP in the most recent Received header");
    }
    return Optional.ofNullable(lastIp);
  }

  /**
   * The domain to check against SPF: the envelope sender's ({@code Return-Path}, written by the
   * final delivering MTA from the SMTP MAIL FROM) if available, otherwise the one from the
   * displayed {@code From} header.
   */
  public static Optional<String> extractSenderDomain(Message message) throws MessagingException {
    return extractSenderDomain(message, DEFAULT_LOGGER);
  }

  /** Like {@link #extractSenderDomain(Message)}, but logs (FINE level) which source was used. */
  public static Optional<String> extractSenderDomain(Message message, Logger logger) throws MessagingException {
    Optional<String> fromReturnPath = extractDomainFromReturnPath(message);
    if (fromReturnPath.isPresent()) {
      logger.fine(() -> "Sender domain from Return-Path: " + fromReturnPath.get());
      return fromReturnPath;
    }
    Optional<String> fromFrom = extractDomainFromFrom(message);
    if (fromFrom.isPresent()) {
      logger.fine(() -> "No usable Return-Path, sender domain from From: " + fromFrom.get());
    } else {
      logger.fine(() -> "Could not determine a sender domain from Return-Path or From");
    }
    return fromFrom;
  }

  private static Optional<String> extractDomainFromReturnPath(Message message) throws MessagingException {
    String[] returnPath = message.getHeader("Return-Path");
    if (returnPath == null || returnPath.length == 0) return Optional.empty();
    String value = returnPath[0].trim();
    if (value.startsWith("<") && value.endsWith(">")) value = value.substring(1, value.length() - 1);
    return domainOf(value);
  }

  private static Optional<String> extractDomainFromFrom(Message message) throws MessagingException {
    Address[] froms = message.getFrom();
    if (froms == null || froms.length != 1) return Optional.empty();
    String raw = froms[0] instanceof InternetAddress ? ((InternetAddress) froms[0]).getAddress() : froms[0].toString();
    return domainOf(raw);
  }

  private static Optional<String> domainOf(String address) {
    if (address == null) return Optional.empty();
    int at = address.lastIndexOf('@');
    if (at < 0 || at >= address.length() - 1) return Optional.empty();
    return Optional.of(address.substring(at + 1));
  }
}
