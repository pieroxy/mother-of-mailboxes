package net.pieroxy.imf.utils.mail;

import net.pieroxy.imf.config.credentials.Credential;
import net.pieroxy.imf.config.general.MailAccountConfiguration;

import javax.mail.MessagingException;

/**
 * Builds an {@link ImapMailbox} from an account's config and resolved credential. Lets {@link
 * net.pieroxy.imf.rules.MailAccount} depend on this step via injection rather than calling
 * {@link ImapMailboxConnection#connect} directly, so it can be tested without real IMAPS/TLS
 * (see {@code GreenMailImapFixture} on the test side).
 */
@FunctionalInterface
public interface ImapMailboxFactory {
  ImapMailbox connect(MailAccountConfiguration config, Credential credential) throws MessagingException;
}
