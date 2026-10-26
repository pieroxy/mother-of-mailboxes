package net.pieroxy.mom.rules.actions.implementations;

import net.pieroxy.mom.config.general.MailFilterRuleActionConfiguration;
import net.pieroxy.mom.rules.RuleContext;
import net.pieroxy.mom.rules.actions.Action;
import net.pieroxy.mom.utils.MailTools;
import net.pieroxy.mom.utils.mail.MailSender;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sends a plain-text reply ({@code body}) to the message's {@code Reply-To}, or {@code From}
 * without one, through the account's SMTP settings. Subject: {@code key}, or {@code Re: <original
 * subject>} when blank. Skips (returns false) what must never get an automatic reply (RFC 3834):
 * automated messages, mailing lists, bounces, no-reply senders, the account's own messages, and
 * messages none of the account's addresses (sender address and aliases) is in To or Cc of. Its
 * replies carry {@code Auto-Submitted: auto-replied}, so other responders skip them in turn.
 */
public class ReplyAction extends Action {
  private static final Set<String> BULK_PRECEDENCES = Set.of("bulk", "list", "junk");
  private static final Set<String> LIST_HEADERS = Set.of("List-Id", "List-Unsubscribe", "List-Post");
  // Compared with separators (".", "-", "_") removed: covers no-reply, no_reply, do-not-reply...
  private static final Set<String> NO_REPLY_PREFIXES = Set.of("noreply", "donotreply", "mailerdaemon", "postmaster");

  private MailSender sender;
  private String body;

  @Override
  protected void bindContext(RuleContext context) {
    sender = context.mailSender();
  }

  @Override
  public void setConfig(MailFilterRuleActionConfiguration config) {
    super.setConfig(config);
    if (sender == null) {
      throw new IllegalStateException(config.getType() + " needs the account's SMTP settings.");
    }
    body = validateBody(config.getBody());
  }

  /** @throws IllegalArgumentException if {@code body} is blank. */
  public static String validateBody(String body) {
    if (body == null || body.isBlank()) {
      throw new IllegalArgumentException("A reply needs a body.");
    }
    return body;
  }

  @Override
  public boolean run(Message message) throws MessagingException {
    InternetAddress recipient = recipientOf(message);
    String skipReason = skipReason(message, recipient);
    if (skipReason != null) {
      getLogger().info(() -> "No reply to " + MailTools.describeFromSafely(message) + ": " + skipReason);
      return false;
    }
    sender.send(buildReply(message, recipient), recipient);
    getLogger().fine(() -> "Replied to " + recipient.getAddress());
    return true;
  }

  /** {@code Reply-To}, or {@code From} without one (see {@link Message#getReplyTo}); null if neither is a usable address. */
  private static InternetAddress recipientOf(Message message) {
    try {
      Address[] replyTo = message.getReplyTo();
      if (replyTo == null || replyTo.length == 0 || !(replyTo[0] instanceof InternetAddress address)) return null;
      return address.getAddress() == null || address.getAddress().isBlank() ? null : address;
    } catch (MessagingException e) {
      return null;
    }
  }

  /** Why this message must not get a reply, or null if it may. Package-visible for testing. */
  String skipReason(Message message, InternetAddress recipient) throws MessagingException {
    String autoSubmitted = firstHeader(message, "Auto-Submitted");
    if (autoSubmitted != null && !headerValue(autoSubmitted).equals("no")) {
      return "automated message (Auto-Submitted: " + autoSubmitted.trim() + ")";
    }
    String precedence = firstHeader(message, "Precedence");
    if (precedence != null && BULK_PRECEDENCES.contains(headerValue(precedence))) {
      return "bulk message (Precedence: " + precedence.trim() + ")";
    }
    for (String listHeader : LIST_HEADERS) {
      if (firstHeader(message, listHeader) != null) return "mailing list message (" + listHeader + ")";
    }
    String returnPath = firstHeader(message, "Return-Path");
    if (returnPath != null && returnPath.replaceAll("[<>\\s]", "").isEmpty()) {
      return "bounce (empty Return-Path)";
    }
    if (recipient == null) return "no address to reply to";
    String recipientAddress = recipient.getAddress().toLowerCase(Locale.ROOT);
    if (isOwnAddress(recipientAddress) || addressesOf(message.getFrom()).stream().anyMatch(this::isOwnAddress)) {
      return "sent by this account";
    }
    boolean addressedToAccount = addressesOf(message.getRecipients(Message.RecipientType.TO)).stream().anyMatch(this::isOwnAddress)
        || addressesOf(message.getRecipients(Message.RecipientType.CC)).stream().anyMatch(this::isOwnAddress);
    if (!addressedToAccount) {
      return "none of the account's addresses is in To or Cc (list or Bcc; an alias missing from the account's SMTP settings?)";
    }
    String localPart = recipientAddress.contains("@") ? recipientAddress.substring(0, recipientAddress.lastIndexOf('@')) : recipientAddress;
    String compactLocalPart = localPart.replaceAll("[._-]", "");
    if (NO_REPLY_PREFIXES.stream().anyMatch(compactLocalPart::startsWith)
        || localPart.startsWith("owner-") || localPart.endsWith("-request")) {
      return "automated sender address (" + recipient.getAddress() + ")";
    }
    return null;
  }

  private MimeMessage buildReply(Message original, InternetAddress recipient) throws MessagingException {
    MimeMessage reply = new MimeMessage(sender.getSession());
    reply.setFrom(new InternetAddress(sender.getFromAddress()));
    reply.setRecipient(Message.RecipientType.TO, recipient);
    reply.setSubject(subjectFor(original), "UTF-8");
    reply.setSentDate(new Date());
    reply.setHeader("Auto-Submitted", "auto-replied");
    String messageId = MailTools.messageIdSafely(original);
    if (messageId != null) {
      reply.setHeader("In-Reply-To", messageId);
      String references = firstHeader(original, "References");
      reply.setHeader("References", references != null ? references.trim() + " " + messageId : messageId);
    }
    reply.setText(body, "UTF-8");
    reply.saveChanges();
    return reply;
  }

  private String subjectFor(Message original) throws MessagingException {
    String key = getConfig().getKey();
    if (key != null && !key.isBlank()) return key.trim();
    String subject = original.getSubject() != null ? original.getSubject().trim() : "";
    return subject.regionMatches(true, 0, "Re:", 0, 3) ? subject : "Re: " + subject;
  }

  /** {@code address} (lower-cased) is the sender address, an alias, or at an aliased {@code @domain}. */
  private boolean isOwnAddress(String address) {
    if (address.equals(sender.getFromAddress().toLowerCase(Locale.ROOT))) return true;
    for (String alias : sender.getAliases()) {
      String lowerAlias = alias.toLowerCase(Locale.ROOT);
      if (lowerAlias.startsWith("@") ? address.endsWith(lowerAlias) : address.equals(lowerAlias)) return true;
    }
    return false;
  }

  /** Lower-cased. */
  private static Set<String> addressesOf(Address[] addresses) {
    if (addresses == null) return Set.of();
    return Arrays.stream(addresses)
        .filter(InternetAddress.class::isInstance)
        .map(a -> ((InternetAddress) a).getAddress())
        .filter(Objects::nonNull)
        .map(a -> a.toLowerCase(Locale.ROOT))
        .collect(Collectors.toSet());
  }

  private static String firstHeader(Message message, String name) throws MessagingException {
    String[] values = message.getHeader(name);
    return values == null || values.length == 0 ? null : values[0];
  }

  /** The value without its parameters or comments, lower-cased: {@code "Auto-Replied; foo"} -> {@code "auto-replied"}. */
  private static String headerValue(String header) {
    return header.split("[;(]", 2)[0].trim().toLowerCase(Locale.ROOT);
  }
}
