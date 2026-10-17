package net.pieroxy.mom.rules.matchers.implementations;

import net.pieroxy.mom.rules.matchers.MatchResult;
import net.pieroxy.mom.rules.matchers.Matcher;
import net.pieroxy.mom.detection.spf.DnsJavaSpfDnsResolver;
import net.pieroxy.mom.detection.spf.SpfEvaluator;
import net.pieroxy.mom.detection.spf.SpfIdentityExtractor;
import net.pieroxy.mom.detection.spf.SpfResult;

import javax.mail.Message;
import javax.mail.MessagingException;
import java.util.Optional;

/**
 * Compares the result of an SPF check (e.g. "pass", "fail", "softfail", "neutral", "none")
 * against the configured key, case-insensitively.
 * <p>
 * The check is always redone ourselves, live, from the connecting IP (read from the most recent
 * {@code Received} header) and the sender domain (see {@link SpfIdentityExtractor}) — never read
 * from a pre-existing {@code Authentication-Results} or {@code Received-SPF} header on the
 * message. Such a header could have been added by the sender itself (nothing prevents it), and
 * nothing guarantees the receiving infrastructure strips it before delivery: it's therefore
 * never trusted, even when present.
 */
public class SpfResultMatcher extends Matcher {
  private final SpfEvaluator evaluator;

  public SpfResultMatcher() {
    this(new SpfEvaluator(new DnsJavaSpfDnsResolver()));
  }

  /** Visible for tests: allows injecting an evaluator with no real DNS resolution. */
  SpfResultMatcher(SpfEvaluator evaluator) {
    this.evaluator = evaluator;
  }

  @Override
  public MatchResult matches(Message message) throws MessagingException {
    String result = evaluateLive(message);
    Optional<String> hit = result != null ? matchingKey(result, String::equalsIgnoreCase) : Optional.empty();
    getLogger().fine(() -> "tested spf result=" + result + " against " + describeKey()
            + " -> " + (hit.isPresent() ? "match" : "no match"));
    return hit.map(this::matched).orElseGet(this::notMatched);
  }

  @Override
  public String extractKeyFromExample(Message message) throws MessagingException {
    String result = evaluateLive(message);
    if (result == null) {
      throw new MessagingException("Cannot learn a SPF_RESULT_EQUALS rule: could not determine an SPF result for this message");
    }
    return result;
  }

  private String evaluateLive(Message message) throws MessagingException {
    // getLogger() (level driven by THIS rule's "logLevel" in the JSON) is passed all the way
    // down through extraction and evaluation, so that "logLevel": "DEBUG" on the rule is enough
    // to see the full trace (Received header examined, SPF record found, mechanism by
    // mechanism) without touching any global logging configuration.
    Optional<String> ip = SpfIdentityExtractor.extractClientIp(message, getLogger());
    Optional<String> domain = SpfIdentityExtractor.extractSenderDomain(message, getLogger());
    if (ip.isEmpty() || domain.isEmpty()) {
      getLogger().fine(() -> "Cannot evaluate SPF live: missing client IP or sender domain");
      return null;
    }
    SpfResult result = evaluator.evaluate(ip.get(), domain.get(), getLogger());
    getLogger().fine(() -> "Evaluated SPF live for ip=" + ip.get() + " domain=" + domain.get() + " -> " + result.getCode());
    return result.getCode();
  }
}
