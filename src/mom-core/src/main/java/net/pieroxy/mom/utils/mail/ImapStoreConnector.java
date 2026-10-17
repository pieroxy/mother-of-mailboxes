package net.pieroxy.mom.utils.mail;

import net.pieroxy.mom.config.credentials.Credential;
import net.pieroxy.mom.config.general.MailAccountConfiguration;

import javax.mail.MessagingException;
import javax.mail.Store;

/**
 * Connects to an account's raw {@link Store}, unlike {@link ImapMailboxFactory} (which wraps it
 * behind the {@link ImapMailbox} abstraction). Only {@link ImapIdleWatcher} needs this: IMAP
 * IDLE requires low-level access (message-count listeners on an {@code IMAPFolder}) that {@link
 * ImapMailbox} doesn't expose. Package-private: lets a test connector be injected without real
 * IMAPS/TLS (see {@code GreenMailImapFixture}).
 */
@FunctionalInterface
interface ImapStoreConnector {
  Store connect(MailAccountConfiguration config, Credential credential) throws MessagingException;
}
