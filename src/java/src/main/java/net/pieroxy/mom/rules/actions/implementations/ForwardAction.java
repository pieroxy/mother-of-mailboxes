package net.pieroxy.mom.rules.actions.implementations;

import net.pieroxy.mom.config.general.MailFilterRuleActionConfiguration;
import net.pieroxy.mom.rules.RuleContext;
import net.pieroxy.mom.rules.actions.Action;
import net.pieroxy.mom.utils.MailTools;
import net.pieroxy.mom.utils.mail.MailSender;

import javax.activation.DataHandler;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Part;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MailDateFormat;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

/**
 * Sends the message to the {@code key} address through the account's SMTP settings.
 * <ul>
 *   <li>FORWARD: a new message from the account's sender address, with the original attached
 *       unchanged.
 *   <li>REDIRECT: the original as is, plus {@code Resent-*} headers. Its body and existing headers
 *       are untouched, so its DKIM signature stays valid.
 * </ul>
 * Both leave the message itself alone (not even marked read). Each hop adds its sender address in
 * {@link #FORWARDED_BY_HEADER}; a message already carrying this account's is skipped, which breaks
 * forwarding loops.
 */
public class ForwardAction extends Action {
  public static final String FORWARDED_BY_HEADER = "X-MOM-Forwarded-By";

  private final boolean redirect;
  private MailSender sender;
  private InternetAddress recipient;

  public ForwardAction(boolean redirect) {
    this.redirect = redirect;
  }

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
    recipient = parseRecipient(config.getKey());
  }

  /** @throws IllegalArgumentException unless {@code key} is a single valid email address. */
  public static InternetAddress parseRecipient(String key) {
    try {
      InternetAddress address = new InternetAddress(key != null ? key.trim() : "", true);
      address.validate();
      return address;
    } catch (AddressException e) {
      throw new IllegalArgumentException("\"" + key + "\" is not a valid recipient email address.");
    }
  }

  @Override
  public boolean run(Message message) throws MessagingException {
    String[] forwardedBy = message.getHeader(FORWARDED_BY_HEADER);
    if (forwardedBy != null && Arrays.stream(forwardedBy).anyMatch(v -> v.trim().equalsIgnoreCase(sender.getFromAddress()))) {
      getLogger().info(() -> "Not sent to " + recipient + ": this account already forwarded this message (loop)");
      return false;
    }
    byte[] raw;
    try {
      raw = MailTools.readRawMessageWithoutMarkingSeen(message);
    } catch (IOException e) {
      throw new MessagingException("Could not read the message", e);
    }
    MimeMessage outgoing = redirect ? buildRedirect(raw) : buildForward(message, raw, forwardedBy);
    sender.send(outgoing, recipient);
    getLogger().fine(() -> (redirect ? "Redirected" : "Forwarded") + " to " + recipient);
    return true;
  }

  /** Not saved on purpose: saveChanges() would rewrite Message-ID and MIME headers, breaking DKIM. */
  private MimeMessage buildRedirect(byte[] raw) throws MessagingException {
    MimeMessage message = new MimeMessage(sender.getSession(), new ByteArrayInputStream(raw));
    message.addHeader("Resent-From", sender.getFromAddress());
    message.addHeader("Resent-To", recipient.toString());
    message.addHeader("Resent-Date", new MailDateFormat().format(new Date()));
    message.addHeader("Resent-Message-ID", "<" + UUID.randomUUID() + "@" + domainOf(sender.getFromAddress()) + ">");
    message.addHeader(FORWARDED_BY_HEADER, sender.getFromAddress());
    return message;
  }

  private MimeMessage buildForward(Message original, byte[] raw, String[] forwardedBy) throws MessagingException {
    String subject = original.getSubject() != null ? original.getSubject() : "";
    MimeMessage message = new MimeMessage(sender.getSession());
    message.setFrom(new InternetAddress(sender.getFromAddress()));
    message.setRecipient(Message.RecipientType.TO, recipient);
    message.setSubject("Fwd: " + subject, "UTF-8");
    message.setSentDate(new Date());
    if (forwardedBy != null) {
      for (String hop : forwardedBy) message.addHeader(FORWARDED_BY_HEADER, hop);
    }
    message.addHeader(FORWARDED_BY_HEADER, sender.getFromAddress());

    MimeBodyPart note = new MimeBodyPart();
    note.setText("Forwarded by MOM (" + describe() + ").\n\n"
        + "From: " + MailTools.describeFromSafely(original) + "\n"
        + "Subject: " + MailTools.describeSubjectSafely(original) + "\n", "UTF-8");
    MimeBodyPart attachment = new MimeBodyPart();
    attachment.setDataHandler(new DataHandler(new ByteArrayDataSource(raw, "message/rfc822")));
    attachment.setDisposition(Part.INLINE);
    MimeMultipart content = new MimeMultipart();
    content.addBodyPart(note);
    content.addBodyPart(attachment);
    message.setContent(content);
    message.saveChanges();
    return message;
  }

  private static String domainOf(String address) {
    int at = address.lastIndexOf('@');
    return at >= 0 ? address.substring(at + 1) : address;
  }
}
