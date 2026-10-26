package net.pieroxy.mom.utils.mail;

import javax.mail.Address;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** Records what it's asked to send instead of sending it. */
public class RecordingMailSender implements MailSender {
  public record Sent(MimeMessage message, Address recipient) {
  }

  public final List<Sent> sent = new ArrayList<>();
  private final String fromAddress;
  private final List<String> aliases;
  private final Session session = Session.getInstance(new Properties());

  public RecordingMailSender(String fromAddress) {
    this(fromAddress, List.of());
  }

  public RecordingMailSender(String fromAddress, List<String> aliases) {
    this.fromAddress = fromAddress;
    this.aliases = aliases;
  }

  @Override
  public String getFromAddress() {
    return fromAddress;
  }

  @Override
  public List<String> getAliases() {
    return aliases;
  }

  @Override
  public Session getSession() {
    return session;
  }

  @Override
  public void send(MimeMessage message, Address recipient) {
    sent.add(new Sent(message, recipient));
  }

  @Override
  public long getSentCount() {
    return sent.size();
  }
}
