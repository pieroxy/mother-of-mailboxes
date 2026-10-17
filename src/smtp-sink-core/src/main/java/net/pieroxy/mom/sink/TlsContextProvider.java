package net.pieroxy.mom.sink;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the STARTTLS {@link SSLContext} from PEM files, and rebuilds it whenever one of them
 * changes on disk (certificate renewal) — checked on each {@link #get()}, which costs only a few
 * file stats. A renewal that fails to load (e.g. caught mid-write) keeps the previous context in
 * use and is retried on the next call.
 */
public class TlsContextProvider {
  private final static Logger LOGGER = Logger.getLogger(TlsContextProvider.class.getName());
  private final static Pattern PRIVATE_KEY = Pattern.compile("-----BEGIN PRIVATE KEY-----(.*?)-----END PRIVATE KEY-----", Pattern.DOTALL);
  private final static char[] KEYSTORE_PASSWORD = new char[0];

  private final SinkConfiguration.TlsFiles files;
  private SSLContext context;
  private long loadedFingerprint;

  /** Loads the files right away: a misconfiguration fails the startup rather than the first STARTTLS. */
  public TlsContextProvider(SinkConfiguration.TlsFiles files) throws IOException, GeneralSecurityException {
    this.files = files;
    this.loadedFingerprint = fingerprint();
    this.context = load();
  }

  public synchronized SSLContext get() {
    long current = fingerprint();
    if (current != loadedFingerprint) {
      try {
        context = load();
        loadedFingerprint = current;
        LOGGER.info("TLS certificate reloaded.");
      } catch (Exception e) {
        LOGGER.log(Level.WARNING, "Could not reload the TLS certificate, keeping the previous one.", e);
      }
    }
    return context;
  }

  /** Changes whenever any of the files' modification time or size does. */
  private long fingerprint() {
    long h = 17;
    for (String path : new String[]{files.getCertFile(), files.getChainFile(), files.getKeyFile()}) {
      if (path == null) continue;
      File f = new File(path);
      h = h * 31 + f.lastModified();
      h = h * 31 + f.length();
    }
    return h;
  }

  private SSLContext load() throws IOException, GeneralSecurityException {
    List<Certificate> chain = new ArrayList<>(readCertificates(files.getCertFile()));
    if (files.getChainFile() != null) chain.addAll(readCertificates(files.getChainFile()));
    if (chain.isEmpty()) throw new GeneralSecurityException("No certificate found in " + files.getCertFile());
    PrivateKey key = readPrivateKey(files.getKeyFile());

    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    keyStore.load(null, null);
    keyStore.setKeyEntry("sink", key, KEYSTORE_PASSWORD, chain.toArray(new Certificate[0]));
    KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    kmf.init(keyStore, KEYSTORE_PASSWORD);
    SSLContext ctx = SSLContext.getInstance("TLS");
    ctx.init(kmf.getKeyManagers(), null, null);
    return ctx;
  }

  private static List<? extends Certificate> readCertificates(String path) throws IOException, GeneralSecurityException {
    byte[] pem = Files.readAllBytes(new File(path).toPath());
    return new ArrayList<>(CertificateFactory.getInstance("X.509").generateCertificates(new ByteArrayInputStream(pem)));
  }

  static PrivateKey readPrivateKey(String path) throws IOException, GeneralSecurityException {
    String pem = Files.readString(new File(path).toPath(), StandardCharsets.US_ASCII);
    Matcher m = PRIVATE_KEY.matcher(pem);
    if (!m.find()) {
      throw new GeneralSecurityException(path + " is not an unencrypted PKCS#8 key (\"BEGIN PRIVATE KEY\"). "
          + "Convert it with: openssl pkcs8 -topk8 -nocrypt -in " + path + " -out privkey-pkcs8.pem");
    }
    PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(Base64.getMimeDecoder().decode(m.group(1)));
    for (String algorithm : new String[]{"RSA", "EC", "Ed25519"}) {
      try {
        return KeyFactory.getInstance(algorithm).generatePrivate(spec);
      } catch (GeneralSecurityException ignored) {
        // Not this algorithm: try the next one.
      }
    }
    throw new GeneralSecurityException("Unsupported private key algorithm in " + path + " (RSA, EC and Ed25519 are).");
  }
}
