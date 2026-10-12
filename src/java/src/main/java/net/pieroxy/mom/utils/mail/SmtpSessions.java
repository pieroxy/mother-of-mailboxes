package net.pieroxy.mom.utils.mail;

import net.pieroxy.mom.config.credentials.Credential;
import net.pieroxy.mom.config.general.MailAccountConfiguration;
import net.pieroxy.mom.config.general.SmtpConfiguration;
import net.pieroxy.mom.config.general.SmtpSecurity;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import java.util.Properties;

/**
 * The only place SMTP connections are opened, with the account's timeouts (see
 * {@link ImapSessions}) and the server's certificate checked against its host name, which
 * javax.mail doesn't do by default.
 */
public final class SmtpSessions {
  private SmtpSessions() {
  }

  /** Opens and authenticates an SMTP connection for the account; the caller closes it. */
  public static Transport connect(MailAccountConfiguration account, SmtpConfiguration smtp, Credential credential) throws MessagingException {
    String protocol = smtp.getSecurity() == SmtpSecurity.TLS ? "smtps" : "smtp";
    String prefix = "mail." + protocol + ".";
    Properties props = new Properties();
    props.setProperty(prefix + "auth", "true");
    props.setProperty(prefix + "ssl.checkserveridentity", "true");
    if (smtp.getSecurity() == SmtpSecurity.STARTTLS) {
      props.setProperty(prefix + "starttls.enable", "true");
      props.setProperty(prefix + "starttls.required", "true");
    }
    long readTimeoutMs = ImapSessions.readTimeoutSeconds(account) * 1000L;
    props.setProperty(prefix + "connectiontimeout", String.valueOf(ImapSessions.connectTimeoutSeconds(account) * 1000L));
    props.setProperty(prefix + "timeout", String.valueOf(readTimeoutMs));
    props.setProperty(prefix + "writetimeout", String.valueOf(readTimeoutMs));
    Transport transport = Session.getInstance(props).getTransport(protocol);
    transport.connect(smtp.getHost(), smtp.getPort(), credential.getUsername(), credential.getPassword());
    return transport;
  }
}
