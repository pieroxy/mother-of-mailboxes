package net.pieroxy.mom.utils.mail;

import net.pieroxy.mom.config.general.MailAccountConfiguration;

import javax.mail.MessagingException;
import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/** Human-readable reason for a failed IMAP/SMTP connection, for the "Test connection" buttons. */
public final class ConnectionFailures {
  private ConnectionFailures() {
  }

  /** Every failure but a bad login surfaces as the same MessagingException type — disambiguated here via its cause chain. */
  public static String describe(MessagingException e, MailAccountConfiguration config) {
    for (Throwable cause = e; cause != null; cause = cause.getCause()) {
      if (cause instanceof UnknownHostException) return "Could not resolve host \"" + cause.getMessage() + "\".";
      if (cause instanceof SocketTimeoutException) return describeTimeout((SocketTimeoutException) cause, config);
      if (cause instanceof ConnectException) return "Connection refused — check the host and port.";
      if (cause instanceof SSLException) return "TLS handshake failed: " + cause.getMessage();
    }
    return e.getMessage() != null ? e.getMessage() : "Connection failed.";
  }

  /** The JDK only tells a connect timeout from a read timeout through its message ("Connect timed out" vs "Read timed out"). */
  private static String describeTimeout(SocketTimeoutException e, MailAccountConfiguration config) {
    if (e.getMessage() != null && e.getMessage().toLowerCase().startsWith("connect")) {
      return "Connection timed out after " + ImapSessions.connectTimeoutSeconds(config) + " seconds.";
    }
    return "Server did not answer within " + ImapSessions.readTimeoutSeconds(config) + " seconds.";
  }
}
