package net.pieroxy.imf.detection.dkim;

import org.apache.james.jdkim.DKIMVerifier;
import org.apache.james.jdkim.api.PublicKeyRecordRetriever;
import org.apache.james.jdkim.api.Result;
import org.apache.james.jdkim.exceptions.FailException;
import org.apache.james.jdkim.exceptions.PermFailException;
import org.apache.james.jdkim.exceptions.TempFailException;
import org.apache.james.jdkim.impl.DNSPublicKeyRecordRetriever;
import org.xbill.DNS.SimpleResolver;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Verifies a message's DKIM signature(s) (RFC 6376), relying on {@code org.apache.james.jdkim}
 * (MIME parsing, canonicalization, cryptography) rather than reimplementing the protocol
 * ourselves — unlike SPF, there's no "just compare strings" shortcut here: this is RSA/Ed25519
 * signature verification over data canonicalized according to precise rules, where a single
 * implementation discrepancy would silently fail signatures that are actually valid.
 * <p>
 * A message can carry several {@code DKIM-Signature} headers (several signers): like an SPF
 * "include", a single valid signature is enough to make the message PASS (RFC 6376 §6.1). When
 * no signature is valid, we keep the most "informative" result among all the failures (FAIL
 * before PERMERROR before TEMPERROR, etc.) — see {@link #PRIORITY}.
 */
public class DkimVerifier {
  // Preference order when no signature is valid: FAIL (a signature present and active but
  // cryptographically invalid) is the strongest, most trustworthy signal, so it takes priority
  // over a plain evaluation error (PERMERROR/TEMPERROR).
  private static final Result.Type[] PRIORITY = {
          Result.Type.FAIL, Result.Type.PERMERROR, Result.Type.TEMPERROR, Result.Type.POLICY, Result.Type.NEUTRAL, Result.Type.NONE
  };

  private final PublicKeyRecordRetriever publicKeyRecordRetriever;
  private final Logger defaultLogger = Logger.getLogger(DkimVerifier.class.getName());

  public DkimVerifier(PublicKeyRecordRetriever publicKeyRecordRetriever) {
    this.publicKeyRecordRetriever = publicKeyRecordRetriever;
  }

  /** Ready-to-use instance, resolving DKIM public keys via the system DNS. */
  public static DkimVerifier createDefault() {
    try {
      SimpleResolver resolver = new SimpleResolver();
      resolver.setTimeout(Duration.ofSeconds(5));
      return new DkimVerifier(new DNSPublicKeyRecordRetriever(resolver));
    } catch (IOException e) {
      throw new IllegalStateException("Could not initialize DNS resolver for DKIM", e);
    }
  }

  /** @return the DKIM result for the raw message (headers + body, as received) read from rawMessage. */
  public DkimResult verify(InputStream rawMessage) {
    return verify(rawMessage, defaultLogger);
  }

  /** Same as {@link #verify(InputStream)}, but logs (FINE level) the per-signature detail to the given logger. */
  public DkimResult verify(InputStream rawMessage, Logger logger) {
    return verifyDetailed(rawMessage, logger).result();
  }

  /**
   * Same as {@link #verify(InputStream, Logger)}, but also exposes the signing domains
   * ({@code d=}) of every signature that actually verified — see {@link DkimVerification}.
   */
  public DkimVerification verifyDetailed(InputStream rawMessage, Logger logger) {
    DKIMVerifier verifier = new DKIMVerifier(publicKeyRecordRetriever);
    // Fallback used only if getResults() below doesn't give us anything usable: when verify()
    // throws, the precise per-signature result (e.g. FAIL for a mismatched body hash) is
    // generally already recorded in getResults() despite the exception — the exception only
    // signals "no signature was accepted as valid."
    DkimResult exceptionFallback;
    try {
      verifier.verify(rawMessage);
      exceptionFallback = null;
    } catch (PermFailException e) {
      logger.fine(() -> "DKIM permanent failure: " + e.getMessage());
      exceptionFallback = DkimResult.PERMERROR;
    } catch (TempFailException e) {
      logger.fine(() -> "DKIM temporary failure: " + e.getMessage());
      exceptionFallback = DkimResult.TEMPERROR;
    } catch (FailException e) {
      // A subtype not covered above (e.g. CompositeFailException): we don't know if it's
      // temporary, so we don't trust the message by default (fail-closed).
      logger.fine(() -> "DKIM verification failed: " + e.getMessage());
      exceptionFallback = DkimResult.PERMERROR;
    } catch (IOException e) {
      // Nothing could be read: no results to consult, we stop here.
      logger.log(Level.FINE, "Failed to read message for DKIM verification", e);
      return new DkimVerification(DkimResult.TEMPERROR, List.of());
    }

    List<Result> results = verifier.getResults();
    if (results != null) {
      for (Result result : results) {
        logger.fine(() -> "DKIM signature result: " + result.getHeaderTextWithReason());
      }
    }
    List<String> passingDomains = results == null ? List.of() : results.stream()
            .filter(Result::isSuccess)
            .map(r -> r.getRecord().getDToken().toString())
            .collect(Collectors.toList());

    if (verifier.hasAnyValidSignature()) {
      return new DkimVerification(DkimResult.PASS, passingDomains);
    }
    if (results == null || results.isEmpty()) {
      if (exceptionFallback != null) return new DkimVerification(exceptionFallback, passingDomains);
      logger.fine(() -> "No DKIM-Signature header on message");
      return new DkimVerification(DkimResult.NONE, passingDomains);
    }
    for (Result.Type type : PRIORITY) {
      if (results.stream().anyMatch(r -> r.getResultType() == type)) {
        return new DkimVerification(DkimResult.valueOf(type.name()), passingDomains);
      }
    }
    return new DkimVerification(exceptionFallback != null ? exceptionFallback : DkimResult.NONE, passingDomains);
  }
}
