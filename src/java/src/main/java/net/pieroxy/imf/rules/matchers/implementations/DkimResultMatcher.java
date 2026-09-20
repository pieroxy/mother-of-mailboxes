package net.pieroxy.imf.rules.matchers.implementations;

import net.pieroxy.imf.detection.dkim.DkimResult;
import net.pieroxy.imf.detection.dkim.DkimVerifier;
import net.pieroxy.imf.rules.matchers.MatchResult;
import net.pieroxy.imf.rules.matchers.Matcher;
import net.pieroxy.imf.utils.MailTools;

import javax.mail.Message;
import javax.mail.MessagingException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;

/**
 * Compares the result of a DKIM check (e.g. "pass", "fail", "none", "permerror") against the
 * configured key, case-insensitively.
 * <p>
 * As with {@link SpfResultMatcher}, the check is always redone ourselves, live, on the raw
 * message (headers + body as received) — never read from a pre-existing
 * {@code Authentication-Results} header, for the same reason: nothing stops the sender from
 * having inserted one themselves. The cryptography (RFC 6376 canonicalization, RSA/Ed25519
 * signature verification) is delegated to {@code org.apache.james.jdkim} via {@link DkimVerifier}
 * rather than reimplemented: unlike SPF, a single implementation discrepancy would silently
 * fail otherwise-valid signatures.
 */
public class DkimResultMatcher extends Matcher {
  private final DkimVerifier verifier;

  public DkimResultMatcher() {
    this(DkimVerifier.createDefault());
  }

  /** Visible for tests: allows injecting a verifier with no real DNS resolution. */
  DkimResultMatcher(DkimVerifier verifier) {
    this.verifier = verifier;
  }

  @Override
  public MatchResult matches(Message message) throws MessagingException {
    String result = evaluateDkim(message);
    Optional<String> hit = result != null ? matchingKey(result, String::equalsIgnoreCase) : Optional.empty();
    getLogger().fine(() -> "tested dkim result=" + result + " against " + describeKey()
            + " -> " + (hit.isPresent() ? "match" : "no match"));
    return hit.map(this::matched).orElseGet(this::notMatched);
  }

  @Override
  public String extractKeyFromExample(Message message) throws MessagingException {
    String result = evaluateDkim(message);
    if (result == null) {
      throw new MessagingException("Cannot learn a DKIM_RESULT_EQUALS rule: could not determine a DKIM result for this message");
    }
    return result;
  }

  private String evaluateDkim(Message message) throws MessagingException {
    byte[] raw;
    try {
      raw = MailTools.readRawMessageWithoutMarkingSeen(message);
    } catch (IOException e) {
      throw new MessagingException("Failed to read message for DKIM verification", e);
    }
    // getLogger() (level driven by THIS rule's "logLevel" in the JSON): same convention as
    // SpfResultMatcher, see its javadoc.
    DkimResult result = verifier.verify(new ByteArrayInputStream(raw), getLogger());
    getLogger().fine(() -> "Evaluated DKIM -> " + result.getCode());
    return result.getCode();
  }
}
