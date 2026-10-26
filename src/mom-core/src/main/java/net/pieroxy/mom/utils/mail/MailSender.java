package net.pieroxy.mom.utils.mail;

import javax.mail.Address;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.util.List;

/** Sends mail on behalf of one account. */
public interface MailSender {
  /** The account's sender address. */
  String getFromAddress();

  /** The account's other addresses: see {@link net.pieroxy.mom.config.general.SmtpConfiguration#getAliases}. Never null. */
  List<String> getAliases();

  /** For building the messages handed to {@link #send}. */
  Session getSession();

  /** Sends {@code message} as is: it's neither saved nor otherwise rewritten. */
  void send(MimeMessage message, Address recipient) throws MessagingException;

  /** Messages sent so far by this sender. */
  long getSentCount();
}
