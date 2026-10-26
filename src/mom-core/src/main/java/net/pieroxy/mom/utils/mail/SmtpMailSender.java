package net.pieroxy.mom.utils.mail;

import net.pieroxy.mom.config.credentials.Credential;
import net.pieroxy.mom.config.general.MailAccountConfiguration;
import net.pieroxy.mom.config.general.SmtpConfiguration;

import javax.mail.Address;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.MimeMessage;
import java.time.Clock;
import java.util.List;

/**
 * One SMTP connection per message: sends are rare, so nothing is kept open between them. Refuses
 * to send more than {@link #MAX_SENDS_PER_HOUR} messages in any rolling hour, so a rule matching a
 * sudden flood can't get the SMTP account suspended.
 */
public class SmtpMailSender implements MailSender {
  public static final int MAX_SENDS_PER_HOUR = 50;

  private final SmtpConfiguration smtp;
  private final Credential credential;
  private final Session session;
  private final SendRateLimit rateLimit;
  private long sentCount;

  public SmtpMailSender(MailAccountConfiguration account, SmtpConfiguration smtp, Credential credential) {
    this(account, smtp, credential, Clock.systemUTC());
  }

  SmtpMailSender(MailAccountConfiguration account, SmtpConfiguration smtp, Credential credential, Clock clock) {
    this.smtp = smtp;
    this.credential = credential;
    this.session = SmtpSessions.session(account, smtp);
    this.rateLimit = new SendRateLimit(MAX_SENDS_PER_HOUR, clock);
  }

  @Override
  public String getFromAddress() {
    return smtp.getFromAddress();
  }

  @Override
  public List<String> getAliases() {
    return smtp.getAliases() != null ? smtp.getAliases() : List.of();
  }

  @Override
  public Session getSession() {
    return session;
  }

  @Override
  public synchronized void send(MimeMessage message, Address recipient) throws MessagingException {
    if (!rateLimit.tryAcquire()) {
      throw new MessagingException("Not sent to " + recipient + ": this account already sent " + MAX_SENDS_PER_HOUR
          + " messages in the last hour.");
    }
    try (Transport transport = SmtpSessions.connect(session, smtp, credential)) {
      transport.sendMessage(message, new Address[]{recipient});
    } catch (MessagingException | RuntimeException e) {
      rateLimit.release();
      throw e;
    }
    sentCount++;
  }

  @Override
  public synchronized long getSentCount() {
    return sentCount;
  }
}
