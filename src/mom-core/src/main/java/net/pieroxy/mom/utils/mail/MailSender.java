package net.pieroxy.mom.utils.mail;

import javax.mail.Address;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

/** Sends mail on behalf of one account. */
public interface MailSender {
  /** The account's sender address. */
  String getFromAddress();

  /** For building the messages handed to {@link #send}. */
  Session getSession();

  /** Sends {@code message} as is: it's neither saved nor otherwise rewritten. */
  void send(MimeMessage message, Address recipient) throws MessagingException;

  /** Messages sent so far by this sender. */
  long getSentCount();
}
