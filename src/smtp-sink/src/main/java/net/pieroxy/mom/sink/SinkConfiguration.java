package net.pieroxy.mom.sink;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The sink's runtime configuration, read from a JSON file (see {@code smtp-sink.example.json}).
 * Every field has a usable default except {@link #hostname}. Relative paths ({@link #storageDir},
 * the {@link TlsFiles} entries) are resolved against the directory holding the config file.
 */
public class SinkConfiguration {
  /** Name this server gives itself: SMTP greeting, EHLO reply and its Received header. Should match the MX record's target. */
  private String hostname;

  private String smtpBindAddress = "0.0.0.0";
  private int smtpPort = 25;
  /**
   * Whether every SMTP connection starts with a PROXY protocol v1 header (nginx stream
   * {@code proxy_protocol on;}, HAProxy {@code send-proxy}). Required if port 25 goes through a
   * TCP proxy: the sending server's real IP is the whole point of the test, and it would
   * otherwise be the proxy's own.
   */
  private boolean smtpProxyProtocol = false;
  /** Idle timeout of an SMTP connection, in seconds. */
  private int smtpTimeoutSeconds = 60;
  /** Simultaneous SMTP connections; any more get a 421 and are closed. */
  private int maxConnections = 50;
  /** Recipient domains accepted (case-insensitive). Empty: any domain. */
  private List<String> acceptedDomains = new ArrayList<>();
  /** Messages bigger than this are refused (advertised in EHLO as SIZE). */
  private int maxMessageSizeBytes = 102_400;

  /** Plain HTTP, meant to sit behind a reverse proxy doing TLS. */
  private String httpBindAddress = "127.0.0.1";
  private int httpPort = 8025;

  /** Where received messages are stored, one file each. */
  private String storageDir = "messages";
  /** How long a received message stays available, in seconds. */
  private int retentionSeconds = 300;

  /** Absent: STARTTLS isn't offered. */
  private TlsFiles tls;

  /** PEM files for STARTTLS — reloaded automatically when they change on disk (see {@link TlsContextProvider}). */
  public static class TlsFiles {
    /** The certificate, optionally followed by its chain (e.g. certbot's fullchain.pem). */
    private String certFile;
    /** Optional: the intermediate chain, if not already appended to {@link #certFile} (e.g. certbot's chain.pem). */
    private String chainFile;
    /** The private key, unencrypted PKCS#8 ("BEGIN PRIVATE KEY", e.g. certbot's privkey.pem). */
    private String keyFile;

    public String getCertFile() {
      return certFile;
    }

    public String getChainFile() {
      return chainFile;
    }

    public String getKeyFile() {
      return keyFile;
    }

    void resolveAgainst(File baseDir) {
      certFile = resolve(baseDir, certFile);
      chainFile = resolve(baseDir, chainFile);
      keyFile = resolve(baseDir, keyFile);
    }
  }

  /** Checks mandatory fields and resolves relative paths against {@code baseDir}. */
  void validateAndResolve(File baseDir) {
    if (hostname == null || hostname.isBlank()) {
      throw new IllegalArgumentException("\"hostname\" is mandatory.");
    }
    if (maxMessageSizeBytes <= 0) throw new IllegalArgumentException("\"maxMessageSizeBytes\" must be positive.");
    if (retentionSeconds <= 0) throw new IllegalArgumentException("\"retentionSeconds\" must be positive.");
    if (maxConnections <= 0) throw new IllegalArgumentException("\"maxConnections\" must be positive.");
    if (smtpTimeoutSeconds <= 0) throw new IllegalArgumentException("\"smtpTimeoutSeconds\" must be positive.");
    if (acceptedDomains == null) acceptedDomains = new ArrayList<>();
    acceptedDomains.replaceAll(d -> d.trim().toLowerCase(Locale.ROOT));
    storageDir = resolve(baseDir, storageDir);
    if (tls != null) {
      if (tls.certFile == null || tls.keyFile == null) {
        throw new IllegalArgumentException("\"tls\" needs at least \"certFile\" and \"keyFile\".");
      }
      tls.resolveAgainst(baseDir);
    }
  }

  private static String resolve(File baseDir, String path) {
    if (path == null) return null;
    File f = new File(path);
    return f.isAbsolute() ? f.getPath() : new File(baseDir, path).getPath();
  }

  public String getHostname() {
    return hostname;
  }

  public void setHostname(String hostname) {
    this.hostname = hostname;
  }

  public String getSmtpBindAddress() {
    return smtpBindAddress;
  }

  public void setSmtpBindAddress(String smtpBindAddress) {
    this.smtpBindAddress = smtpBindAddress;
  }

  public int getSmtpPort() {
    return smtpPort;
  }

  public void setSmtpPort(int smtpPort) {
    this.smtpPort = smtpPort;
  }

  public boolean isSmtpProxyProtocol() {
    return smtpProxyProtocol;
  }

  public void setSmtpProxyProtocol(boolean smtpProxyProtocol) {
    this.smtpProxyProtocol = smtpProxyProtocol;
  }

  public int getSmtpTimeoutSeconds() {
    return smtpTimeoutSeconds;
  }

  public int getMaxConnections() {
    return maxConnections;
  }

  public List<String> getAcceptedDomains() {
    return acceptedDomains;
  }

  public void setAcceptedDomains(List<String> acceptedDomains) {
    this.acceptedDomains = acceptedDomains;
  }

  public int getMaxMessageSizeBytes() {
    return maxMessageSizeBytes;
  }

  public void setMaxMessageSizeBytes(int maxMessageSizeBytes) {
    this.maxMessageSizeBytes = maxMessageSizeBytes;
  }

  public String getHttpBindAddress() {
    return httpBindAddress;
  }

  public void setHttpBindAddress(String httpBindAddress) {
    this.httpBindAddress = httpBindAddress;
  }

  public int getHttpPort() {
    return httpPort;
  }

  public void setHttpPort(int httpPort) {
    this.httpPort = httpPort;
  }

  public String getStorageDir() {
    return storageDir;
  }

  public void setStorageDir(String storageDir) {
    this.storageDir = storageDir;
  }

  public int getRetentionSeconds() {
    return retentionSeconds;
  }

  public void setRetentionSeconds(int retentionSeconds) {
    this.retentionSeconds = retentionSeconds;
  }

  public TlsFiles getTls() {
    return tls;
  }
}
