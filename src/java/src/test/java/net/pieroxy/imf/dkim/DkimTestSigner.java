package net.pieroxy.imf.dkim;

import net.pieroxy.imf.detection.dkim.DkimVerifier;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Base64;
import java.util.Map;

/**
 * Signs a message "by hand" ("simple" canonicalization, RFC 6376 §3.4.1 and §3.4.3), independent
 * of the verification library used in production ({@code org.apache.james.jdkim}), so that
 * {@link DkimVerifier}'s tests validate that third-party library against a signature whose every
 * byte we control — not just a round trip internal to the library itself (which would mask a bug
 * shared by both).
 */
public class DkimTestSigner {
  public static final class SignedMessage {
    public final String rawMessage;
    public final String publicKeyRecord;

    SignedMessage(String rawMessage, String publicKeyRecord) {
      this.rawMessage = rawMessage;
      this.publicKeyRecord = publicKeyRecord;
    }
  }

  /** headers must preserve insertion order (e.g. LinkedHashMap). */
  public static SignedMessage sign(String selector, String domain, Map<String, String> headers, String body) throws Exception {
    KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
    keyGen.initialize(2048);
    KeyPair keyPair = keyGen.generateKeyPair();

    String bodyHash = base64(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
    String signedHeaderNames = String.join(":", headers.keySet());

    // The "b=" tag is present but empty at the point of computing the signature (RFC 6376 §3.7
    // step 4), and the DKIM-Signature header itself does NOT have a trailing CRLF in the signed
    // input.
    String dkimHeaderPrefix = "DKIM-Signature: v=1; a=rsa-sha256; c=simple/simple; d=" + domain + "; s=" + selector
            + "; h=" + signedHeaderNames + "; bh=" + bodyHash + "; b=";

    StringBuilder signingInput = new StringBuilder();
    for (Map.Entry<String, String> header : headers.entrySet()) {
      signingInput.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
    }
    signingInput.append(dkimHeaderPrefix);

    Signature signer = Signature.getInstance("SHA256withRSA");
    signer.initSign(keyPair.getPrivate());
    signer.update(signingInput.toString().getBytes(StandardCharsets.US_ASCII));
    String signature = base64(signer.sign());

    StringBuilder raw = new StringBuilder();
    for (Map.Entry<String, String> header : headers.entrySet()) {
      raw.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
    }
    raw.append(dkimHeaderPrefix).append(signature).append("\r\n");
    raw.append("\r\n").append(body);

    String publicKeyRecord = "v=DKIM1; k=rsa; p=" + base64(keyPair.getPublic().getEncoded());
    return new SignedMessage(raw.toString(), publicKeyRecord);
  }

  private static String base64(byte[] data) {
    return Base64.getEncoder().encodeToString(data);
  }
}
