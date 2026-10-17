package net.pieroxy.mom.utils.mail;

import net.pieroxy.mom.config.credentials.Credential;
import net.pieroxy.mom.config.general.MailAccountConfiguration;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Store;
import java.util.Properties;

/**
 * The only place IMAP connections are opened, so none can bypass the account's connect and
 * read/write timeouts (without them, javax.mail blocks on a stalled server indefinitely).
 * <p>
 * Uses {@link Session#getInstance} rather than {@link Session#getDefaultInstance}: the latter is a
 * JVM-wide singleton that silently ignores the properties of every call after the first.
 */
public final class ImapSessions {
  public final static int DEFAULT_CONNECT_TIMEOUT_SECONDS = 5;
  public final static int DEFAULT_READ_TIMEOUT_SECONDS = 60;

  private ImapSessions() {
  }

  public static int connectTimeoutSeconds(MailAccountConfiguration config) {
    return config.getConnectTimeout() > 0 ? config.getConnectTimeout() : DEFAULT_CONNECT_TIMEOUT_SECONDS;
  }

  public static int readTimeoutSeconds(MailAccountConfiguration config) {
    return config.getReadTimeout() > 0 ? config.getReadTimeout() : DEFAULT_READ_TIMEOUT_SECONDS;
  }

  /**
   * Opens an IMAPS connection to the account.
   *
   * @param extraReadTimeoutMs added to the account's read timeout, for callers whose reads
   *     legitimately block longer than any single command should (IMAP IDLE).
   */
  public static Store connect(MailAccountConfiguration config, Credential credential, long extraReadTimeoutMs) throws MessagingException {
    long readTimeoutMs = readTimeoutSeconds(config) * 1000L + extraReadTimeoutMs;
    Properties props = new Properties();
    // Only covers automatic prefetch (FetchProfile): message.writeTo() still sends BODY[], hence
    // the per-message setPeek(true) in MailTools#readRawMessageWithoutMarkingSeen.
    props.setProperty("mail.imaps.peek", "true");
    props.setProperty("mail.imaps.connectiontimeout", String.valueOf(connectTimeoutSeconds(config) * 1000L));
    props.setProperty("mail.imaps.timeout", String.valueOf(readTimeoutMs));
    props.setProperty("mail.imaps.writetimeout", String.valueOf(readTimeoutMs));
    Store store = Session.getInstance(props).getStore("imaps");
    store.connect(config.getHost(), config.getPort(), credential.getUsername(), credential.getPassword());
    return store;
  }
}
