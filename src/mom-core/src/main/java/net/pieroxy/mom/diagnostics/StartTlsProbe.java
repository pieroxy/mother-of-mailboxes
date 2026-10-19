package net.pieroxy.mom.diagnostics;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Connects to a mail server on port 25, upgrades the connection with STARTTLS, and returns the
 * certificate chain it presents — without judging it: {@link DaneCheck} matches it against the
 * TLSA records itself, so no certificate authority is involved (and none is trusted or refused).
 */
public class StartTlsProbe {
  static int connectTimeoutMs = 8_000;
  static int readTimeoutMs = 10_000;

  /** The server doesn't offer STARTTLS. */
  public static class NoStartTlsException extends IOException {
    public NoStartTlsException(String message) {
      super(message);
    }
  }

  /** @return the presented chain, leaf first. */
  public List<X509Certificate> certificates(String host, int port) throws IOException {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
      socket.setSoTimeout(readTimeoutMs);
      BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
      OutputStream out = socket.getOutputStream();
      expect(in, "220", host);
      send(out, "EHLO " + localName());
      List<String> ehlo = expect(in, "250", host);
      if (ehlo.stream().noneMatch(l -> l.length() > 4 && l.substring(4).trim().toUpperCase(Locale.ROOT).startsWith("STARTTLS"))) {
        throw new NoStartTlsException(host + " doesn't offer STARTTLS");
      }
      send(out, "STARTTLS");
      expect(in, "220", host);

      SSLContext ctx = SSLContext.getInstance("TLS");
      ctx.init(null, new TrustManager[]{new AcceptAll()}, null);
      SSLSocket ssl = (SSLSocket) ctx.getSocketFactory().createSocket(socket, host, port, true);
      SSLParameters params = ssl.getSSLParameters();
      params.setServerNames(List.of(new SNIHostName(host)));
      ssl.setSSLParameters(params);
      ssl.setUseClientMode(true);
      ssl.startHandshake();
      List<X509Certificate> chain = new ArrayList<>();
      for (Certificate c : ssl.getSession().getPeerCertificates()) {
        if (c instanceof X509Certificate x) chain.add(x);
      }
      try {
        send(ssl.getOutputStream(), "QUIT");
      } catch (IOException ignored) {
        // Done anyway.
      }
      return chain;
    } catch (GeneralSecurityException e) {
      throw new IOException("TLS setup failed: " + e.getMessage(), e);
    }
  }

  private static void send(OutputStream out, String line) throws IOException {
    out.write((line + "\r\n").getBytes(StandardCharsets.US_ASCII));
    out.flush();
  }

  /** Reads a (possibly multi-line) reply, which must have {@code code}. */
  private static List<String> expect(BufferedReader in, String code, String host) throws IOException {
    List<String> lines = new ArrayList<>();
    String line;
    do {
      line = in.readLine();
      if (line == null) throw new IOException(host + " closed the connection");
      lines.add(line);
    } while (line.length() > 3 && line.charAt(3) == '-');
    if (!line.startsWith(code)) throw new IOException(host + " answered: " + line);
    return lines;
  }

  private static String localName() {
    try {
      String name = InetAddress.getLocalHost().getCanonicalHostName();
      return name.contains(".") ? name : "mom.invalid";
    } catch (IOException e) {
      return "mom.invalid";
    }
  }

  private static final class AcceptAll implements X509TrustManager {
    public void checkClientTrusted(X509Certificate[] chain, String authType) {}

    public void checkServerTrusted(X509Certificate[] chain, String authType) {}

    public X509Certificate[] getAcceptedIssuers() {
      return new X509Certificate[0];
    }
  }
}
