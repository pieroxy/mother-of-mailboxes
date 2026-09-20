package net.pieroxy.imf.detection.dmarc;

import net.pieroxy.imf.detection.dkim.DkimVerification;
import net.pieroxy.imf.detection.dkim.DkimVerifier;
import net.pieroxy.imf.detection.spf.SpfEvaluator;
import net.pieroxy.imf.detection.spf.SpfIdentityExtractor;
import net.pieroxy.imf.detection.spf.SpfResult;
import net.pieroxy.imf.utils.MailTools;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Computes the full DMARC evaluation of a message (From domain, underlying SPF and DKIM, then
 * {@link DmarcEvaluator}) — shared by {@code DmarcResultMatcher} and {@code DmarcPolicyMatcher},
 * which each only need half of the result (pass/fail for one, the published policy for the
 * other) but the same underlying computation.
 */
public class DmarcMessageEvaluator {
  private final SpfEvaluator spfEvaluator;
  private final DkimVerifier dkimVerifier;
  private final DmarcEvaluator dmarcEvaluator;

  public DmarcMessageEvaluator(SpfEvaluator spfEvaluator, DkimVerifier dkimVerifier, DmarcEvaluator dmarcEvaluator) {
    this.spfEvaluator = spfEvaluator;
    this.dkimVerifier = dkimVerifier;
    this.dmarcEvaluator = dmarcEvaluator;
  }

  /** @return empty if the message's From domain can't be determined (no single usable From address). */
  public Optional<DmarcEvaluation> evaluate(Message message, Logger logger) throws MessagingException {
    Optional<String> fromDomain = extractFromDomain(message);
    if (fromDomain.isEmpty()) {
      logger.fine(() -> "Cannot evaluate DMARC: no single usable From address");
      return Optional.empty();
    }

    // DMARC needs the domain SPF actually verified (Return-Path, falling back to From) —
    // distinct from the From domain itself, which is used for alignment.
    Optional<String> spfIp = SpfIdentityExtractor.extractClientIp(message, logger);
    Optional<String> spfDomain = SpfIdentityExtractor.extractSenderDomain(message, logger);
    boolean spfPassed = spfIp.isPresent() && spfDomain.isPresent()
            && spfEvaluator.evaluate(spfIp.get(), spfDomain.get(), logger) == SpfResult.PASS;

    byte[] raw;
    try {
      raw = MailTools.readRawMessageWithoutMarkingSeen(message);
    } catch (IOException e) {
      throw new MessagingException("Failed to read message for DMARC verification", e);
    }
    DkimVerification dkimVerification = dkimVerifier.verifyDetailed(new ByteArrayInputStream(raw), logger);

    DmarcEvaluation evaluation = dmarcEvaluator.evaluateDetailed(fromDomain.get(), spfPassed, spfDomain.orElse(null),
            dkimVerification.passingDomains(), logger);
    logger.fine(() -> "Evaluated DMARC for from=" + fromDomain.get() + " -> result=" + evaluation.result().getCode()
            + " policy=" + evaluation.policy().getCode());
    return Optional.of(evaluation);
  }

  private static Optional<String> extractFromDomain(Message message) throws MessagingException {
    Address[] froms = message.getFrom();
    if (froms == null || froms.length != 1) return Optional.empty();
    String raw = froms[0] instanceof InternetAddress ? ((InternetAddress) froms[0]).getAddress() : froms[0].toString();
    if (raw == null) return Optional.empty();
    int at = raw.lastIndexOf('@');
    if (at < 0 || at >= raw.length() - 1) return Optional.empty();
    return Optional.of(raw.substring(at + 1));
  }
}
