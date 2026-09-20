package net.pieroxy.imf.rules.matchers.implementations;

import net.pieroxy.imf.detection.fcrdns.DnsJavaFcrdnsDnsResolver;
import net.pieroxy.imf.detection.fcrdns.FcrdnsEvaluator;
import net.pieroxy.imf.detection.fcrdns.FcrdnsResult;
import net.pieroxy.imf.rules.matchers.MatchResult;
import net.pieroxy.imf.rules.matchers.Matcher;
import net.pieroxy.imf.detection.spf.SpfIdentityExtractor;

import javax.mail.Message;
import javax.mail.MessagingException;
import java.util.Optional;

/**
 * Compares the result of an FCrDNS (Forward-Confirmed reverse DNS) check against the configured
 * key: {@code pass}, {@code fail}, {@code none}, or {@code temperror}. Case-insensitive.
 * <p>
 * Unlike {@link SpfResultMatcher}/{@link DkimResultMatcher}/{@link DmarcResultMatcher}, this
 * isn't a domain authentication standard — it says nothing about the sender domain
 * ({@code From:}/{@code Return-Path}), only whether the connecting IP has a consistent,
 * forward-confirmed reverse DNS. See {@link FcrdnsEvaluator} for details and its limitations.
 */
public class FcrdnsResultMatcher extends Matcher {
  private final FcrdnsEvaluator evaluator;

  public FcrdnsResultMatcher() {
    this(new FcrdnsEvaluator(new DnsJavaFcrdnsDnsResolver()));
  }

  /** Visible for tests: allows injecting an evaluator with no real DNS resolution. */
  FcrdnsResultMatcher(FcrdnsEvaluator evaluator) {
    this.evaluator = evaluator;
  }

  @Override
  public MatchResult matches(Message message) throws MessagingException {
    String result = evaluateFcrdns(message);
    Optional<String> hit = result != null ? matchingKey(result, String::equalsIgnoreCase) : Optional.empty();
    getLogger().fine(() -> "tested fcrdns result=" + result + " against " + describeKey()
            + " -> " + (hit.isPresent() ? "match" : "no match"));
    return hit.map(this::matched).orElseGet(this::notMatched);
  }

  @Override
  public String extractKeyFromExample(Message message) throws MessagingException {
    String result = evaluateFcrdns(message);
    if (result == null) {
      throw new MessagingException("Cannot learn a FCRDNS_RESULT_EQUALS rule: could not determine the connecting IP for this message");
    }
    return result;
  }

  private String evaluateFcrdns(Message message) throws MessagingException {
    Optional<String> ip = SpfIdentityExtractor.extractClientIp(message, getLogger());
    if (ip.isEmpty()) {
      getLogger().fine(() -> "Cannot evaluate FCrDNS: no connecting IP found");
      return null;
    }
    FcrdnsResult result = evaluator.evaluate(ip.get(), getLogger());
    getLogger().fine(() -> "Evaluated FCrDNS for ip=" + ip.get() + " -> " + result.getCode());
    return result.getCode();
  }
}
