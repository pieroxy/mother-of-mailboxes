package net.pieroxy.mom.sink;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One SMTP connection, receive-only (RFC 5321 subset: HELO/EHLO, STARTTLS, MAIL, RCPT, DATA,
 * RSET, NOOP, VRFY, QUIT). A message is only kept if its Subject is a UUID — anything else is
 * refused at the end of DATA, so the sink can't double as a disposable inbox: a confirmation mail
 * from some website never has a UUID as its subject. A kept message is stored with a
 * {@code Return-Path} and a {@code Received} header prepended, recording what only this end of the
 * connection knows: the client's IP, its HELO/EHLO name, and the TLS protocol and cipher used.
 */
class SmtpSession implements Runnable {
  private final static Logger LOGGER = Logger.getLogger(SmtpSession.class.getName());
  private final static int MAX_COMMAND_LINE = 2048;
  private final static int MAX_PROXY_LINE = 107; // PROXY protocol v1 spec, CRLF included.
  private final static int MAX_RECIPIENTS = 10;
  private final static DateTimeFormatter RECEIVED_DATE = DateTimeFormatter.RFC_1123_DATE_TIME;

  private final SinkConfiguration config;
  private final TlsContextProvider tls;
  private final MessageStore store;
  private Socket socket;
  private LineReader in;
  private OutputStream out;

  private String clientIp;
  private String helo;
  private boolean esmtp;
  private String tlsProtocol;
  private String tlsCipher;
  private String mailFrom;
  private final List<String> recipients = new ArrayList<>();

  SmtpSession(Socket socket, SinkConfiguration config, TlsContextProvider tls, MessageStore store) {
    this.socket = socket;
    this.config = config;
    this.tls = tls;
    this.store = store;
  }

  @Override
  public void run() {
    try {
      socket.setSoTimeout(config.getSmtpTimeoutSeconds() * 1000);
      in = new LineReader(socket.getInputStream());
      out = socket.getOutputStream();
      clientIp = socket.getInetAddress().getHostAddress();
      if (config.isSmtpProxyProtocol() && !readProxyHeader()) return;

      reply("220 " + config.getHostname() + " ESMTP MOM SMTP sink");
      LineReader.Line line;
      while ((line = in.read(MAX_COMMAND_LINE)) != null) {
        if (line.overflow()) {
          reply("500 5.5.2 Line too long");
          continue;
        }
        if (!handle(line.asString())) return;
      }
    } catch (IOException e) {
      LOGGER.log(Level.FINE, "Connection from " + clientIp + " ended: " + e.getMessage());
    } catch (RuntimeException e) {
      LOGGER.log(Level.WARNING, "Unexpected error in the session with " + clientIp, e);
    } finally {
      try {
        socket.close(); // The TLS socket once STARTTLS happened, which closes the plain one with it.
      } catch (IOException ignored) {
        // Nothing left to do with it.
      }
    }
  }

  /** Reads "PROXY TCP4|TCP6 &lt;src&gt; &lt;dst&gt; &lt;sport&gt; &lt;dport&gt;" (or "PROXY UNKNOWN") and adopts its source IP. */
  private boolean readProxyHeader() throws IOException {
    LineReader.Line line = in.read(MAX_PROXY_LINE);
    if (line == null || line.overflow()) return false;
    String[] parts = line.asString().split(" ");
    if (parts.length < 2 || !parts[0].equals("PROXY")) {
      LOGGER.warning("Missing PROXY protocol header from " + clientIp + ": closing.");
      return false;
    }
    if ((parts[1].equals("TCP4") || parts[1].equals("TCP6")) && parts.length == 6) {
      clientIp = parts[2];
    } else if (!parts[1].equals("UNKNOWN")) {
      LOGGER.warning("Malformed PROXY protocol header from " + clientIp + ": closing.");
      return false;
    }
    return true;
  }

  /** @return false once the connection must be closed. */
  private boolean handle(String line) throws IOException {
    int space = line.indexOf(' ');
    String verb = (space < 0 ? line : line.substring(0, space)).toUpperCase(Locale.ROOT);
    String arg = space < 0 ? "" : line.substring(space + 1).trim();

    switch (verb) {
      case "EHLO":
      case "HELO":
        if (arg.isEmpty()) {
          reply("501 5.5.4 Syntax: " + verb + " hostname");
          return true;
        }
        helo = arg;
        esmtp = verb.equals("EHLO");
        resetTransaction();
        if (esmtp) {
          List<String> lines = new ArrayList<>();
          lines.add(config.getHostname());
          lines.add("SIZE " + config.getMaxMessageSizeBytes());
          lines.add("8BITMIME");
          lines.add("SMTPUTF8");
          if (tls != null && tlsProtocol == null) lines.add("STARTTLS");
          replyMulti(250, lines);
        } else {
          reply("250 " + config.getHostname());
        }
        return true;
      case "STARTTLS":
        return startTls(arg);
      case "MAIL":
        mail(arg);
        return true;
      case "RCPT":
        rcpt(arg);
        return true;
      case "DATA":
        data();
        return true;
      case "RSET":
        resetTransaction();
        reply("250 2.0.0 OK");
        return true;
      case "NOOP":
        reply("250 2.0.0 OK");
        return true;
      case "VRFY":
        reply("252 2.1.5 Cannot verify, but will accept");
        return true;
      case "QUIT":
        reply("221 2.0.0 Bye");
        return false;
      default:
        reply("502 5.5.1 Command not implemented");
        return true;
    }
  }

  private boolean startTls(String arg) throws IOException {
    if (tls == null || tlsProtocol != null) {
      reply("502 5.5.1 STARTTLS not available");
      return true;
    }
    if (!arg.isEmpty()) {
      reply("501 5.5.4 Syntax: STARTTLS");
      return true;
    }
    if (in.hasPendingInput()) {
      // Anything pipelined after STARTTLS would be read as if it had come through TLS (RFC 3207 §4.2).
      reply("554 5.5.1 Commands pipelined after STARTTLS");
      return false;
    }
    reply("220 2.0.0 Ready to start TLS");
    SSLContext ctx = tls.get();
    SSLSocket ssl = (SSLSocket) ctx.getSocketFactory().createSocket(socket, socket.getInetAddress().getHostAddress(), socket.getPort(), true);
    ssl.setUseClientMode(false);
    ssl.setSoTimeout(config.getSmtpTimeoutSeconds() * 1000);
    ssl.startHandshake();
    socket = ssl;
    in = new LineReader(ssl.getInputStream());
    out = ssl.getOutputStream();
    tlsProtocol = ssl.getSession().getProtocol();
    tlsCipher = ssl.getSession().getCipherSuite();
    // RFC 3207 §4.2: everything learned before TLS is forgotten, starting with the EHLO.
    helo = null;
    esmtp = false;
    resetTransaction();
    return true;
  }

  private void mail(String arg) throws IOException {
    if (helo == null) {
      reply("503 5.5.1 Send HELO/EHLO first");
      return;
    }
    if (mailFrom != null) {
      reply("503 5.5.1 Nested MAIL command");
      return;
    }
    Path path = parsePath(arg, "FROM:");
    if (path == null) {
      reply("501 5.5.4 Syntax: MAIL FROM:<address>");
      return;
    }
    for (String param : path.params) {
      if (param.toUpperCase(Locale.ROOT).startsWith("SIZE=")) {
        try {
          if (Long.parseLong(param.substring(5)) > config.getMaxMessageSizeBytes()) {
            reply("552 5.3.4 Message size exceeds the " + config.getMaxMessageSizeBytes() + " bytes limit");
            return;
          }
        } catch (NumberFormatException e) {
          reply("501 5.5.4 Invalid SIZE parameter");
          return;
        }
      }
    }
    mailFrom = path.address;
    reply("250 2.1.0 OK");
  }

  private void rcpt(String arg) throws IOException {
    if (mailFrom == null) {
      reply("503 5.5.1 Send MAIL first");
      return;
    }
    Path path = parsePath(arg, "TO:");
    if (path == null || path.address.isEmpty()) {
      reply("501 5.5.4 Syntax: RCPT TO:<address>");
      return;
    }
    if (recipients.size() >= MAX_RECIPIENTS) {
      reply("452 4.5.3 Too many recipients");
      return;
    }
    String domain = path.address.substring(path.address.lastIndexOf('@') + 1).toLowerCase(Locale.ROOT);
    if (!config.getAcceptedDomains().isEmpty() && !config.getAcceptedDomains().contains(domain)) {
      reply("550 5.7.1 Relay access denied");
      return;
    }
    recipients.add(path.address);
    reply("250 2.1.5 OK");
  }

  private void data() throws IOException {
    if (mailFrom == null || recipients.isEmpty()) {
      reply("503 5.5.1 Send MAIL and RCPT first");
      return;
    }
    reply("354 End data with <CR><LF>.<CR><LF>");

    int max = config.getMaxMessageSizeBytes();
    ByteArrayOutputStream message = new ByteArrayOutputStream();
    boolean tooBig = false;
    LineReader.Line line;
    while (true) {
      line = in.read(max + 2);
      if (line == null) throw new IOException("Connection closed during DATA");
      byte[] bytes = line.bytes();
      if (!line.overflow() && bytes.length == 1 && bytes[0] == '.') break;
      if (tooBig) continue;
      int offset = bytes.length > 0 && bytes[0] == '.' ? 1 : 0; // Dot-unstuffing.
      if (line.overflow() || message.size() + bytes.length - offset + 2 > max) {
        tooBig = true;
        continue;
      }
      message.write(bytes, offset, bytes.length - offset);
      message.write('\r');
      message.write('\n');
    }

    try {
      if (tooBig) {
        reply("552 5.3.4 Message size exceeds the " + max + " bytes limit");
        return;
      }
      byte[] content = message.toByteArray();
      String subject = MessageHeaders.subject(content);
      if (!MessageStore.isUuid(subject)) {
        reply("550 5.7.1 Only test messages whose Subject is a UUID are accepted");
        return;
      }
      ByteArrayOutputStream stored = new ByteArrayOutputStream();
      stored.write(traceHeaders().getBytes(StandardCharsets.UTF_8));
      stored.write(content);
      store.save(subject, stored.toByteArray());
      LOGGER.info("Stored " + subject.toLowerCase(Locale.ROOT) + " from [" + clientIp + "] helo=" + helo + " tls=" + (tlsProtocol == null ? "none" : tlsProtocol));
      reply("250 2.0.0 OK " + subject.toLowerCase(Locale.ROOT));
    } finally {
      resetTransaction();
    }
  }

  /** Return-Path and Received, in the usual format of a final-delivery MTA. */
  private String traceHeaders() {
    String with = (esmtp ? "ESMTP" : "SMTP") + (tlsProtocol != null ? "S" : "");
    StringBuilder sb = new StringBuilder();
    sb.append("Return-Path: <").append(mailFrom).append(">\r\n");
    sb.append("Received: from ").append(helo).append(" (").append(addressLiteral(clientIp)).append(")\r\n");
    sb.append("\tby ").append(config.getHostname()).append(" (MOM SMTP sink) with ").append(with);
    if (tlsProtocol != null) sb.append(" (").append(tlsProtocol).append(':').append(tlsCipher).append(')');
    sb.append("\r\n\tfor <").append(recipients.get(0)).append(">; ")
        .append(RECEIVED_DATE.format(ZonedDateTime.now())).append("\r\n");
    return sb.toString();
  }

  /** RFC 5321 §4.1.3 address literal: [1.2.3.4] or [IPv6:2001:db8::1]. */
  private static String addressLiteral(String ip) {
    return ip.contains(":") ? "[IPv6:" + ip + "]" : "[" + ip + "]";
  }

  private void resetTransaction() {
    mailFrom = null;
    recipients.clear();
  }

  private record Path(String address, List<String> params) {}

  /** Parses "FROM:&lt;addr&gt; PARAM=VALUE ..." (or "TO:..."), tolerating a space after the colon. */
  private static Path parsePath(String arg, String keyword) {
    if (!arg.toUpperCase(Locale.ROOT).startsWith(keyword)) return null;
    String rest = arg.substring(keyword.length()).trim();
    if (!rest.startsWith("<")) return null;
    int end = rest.indexOf('>');
    if (end < 0) return null;
    String address = rest.substring(1, end).trim();
    List<String> params = new ArrayList<>();
    for (String p : rest.substring(end + 1).trim().split("\\s+")) {
      if (!p.isEmpty()) params.add(p);
    }
    return new Path(address, params);
  }

  private void reply(String line) throws IOException {
    out.write((line + "\r\n").getBytes(StandardCharsets.UTF_8));
    out.flush();
  }

  private void replyMulti(int code, List<String> lines) throws IOException {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < lines.size(); i++) {
      sb.append(code).append(i == lines.size() - 1 ? ' ' : '-').append(lines.get(i)).append("\r\n");
    }
    out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
    out.flush();
  }
}
