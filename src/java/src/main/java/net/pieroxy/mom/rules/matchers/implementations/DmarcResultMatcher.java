package net.pieroxy.mom.rules.matchers.implementations;

import net.pieroxy.mom.detection.dkim.DkimVerifier;
import net.pieroxy.mom.detection.dmarc.DmarcEvaluator;
import net.pieroxy.mom.detection.dmarc.DmarcMessageEvaluator;
import net.pieroxy.mom.detection.dmarc.DnsJavaDmarcDnsResolver;
import net.pieroxy.mom.rules.matchers.MatchResult;
import net.pieroxy.mom.rules.matchers.Matcher;
import net.pieroxy.mom.detection.spf.DnsJavaSpfDnsResolver;
import net.pieroxy.mom.detection.spf.SpfEvaluator;

import javax.mail.Message;
import javax.mail.MessagingException;
import java.util.Optional;

/**
 * Compares the result of a DMARC (RFC 7489) evaluation against the configured key: {@code pass},
 * {@code fail}, {@code none}, {@code permerror}, or {@code temperror}. Case-insensitive.
 * <p>
 * Builds on the already-implemented SPF and DKIM ({@link SpfResultMatcher}, {@link DkimResultMatcher}):
 * both are recomputed live (same trust policy as the other matchers in this package — a
 * pre-existing header is never read), then {@link DmarcEvaluator} determines whether either one
 * is "aligned" with the domain shown in {@code From:}.
 * <p>
 * See also {@link DmarcPolicyMatcher}, which exposes the domain's published policy
 * ({@code p=}/{@code sp=}) rather than the pass/fail verdict — the two share the same
 * computation via {@link DmarcMessageEvaluator}.
 */
public class DmarcResultMatcher extends Matcher {
  private final DmarcMessageEvaluator evaluator;

  public DmarcResultMatcher() {
    this(new DmarcMessageEvaluator(new SpfEvaluator(new DnsJavaSpfDnsResolver()),
            DkimVerifier.createDefault(),
            new DmarcEvaluator(new DnsJavaDmarcDnsResolver())));
  }

  /** Visible for tests: allows injecting an evaluator with no network access. */
  DmarcResultMatcher(DmarcMessageEvaluator evaluator) {
    this.evaluator = evaluator;
  }

  @Override
  public MatchResult matches(Message message) throws MessagingException {
    Optional<String> result = evaluate(message);
    Optional<String> hit = result.flatMap(r -> matchingKey(r, String::equalsIgnoreCase));
    getLogger().fine(() -> "tested dmarc result=" + result.orElse(null) + " against " + describeKey()
            + " -> " + (hit.isPresent() ? "match" : "no match"));
    return hit.map(this::matched).orElseGet(this::notMatched);
  }

  @Override
  public String extractKeyFromExample(Message message) throws MessagingException {
    return evaluate(message).orElseThrow(() ->
            new MessagingException("Cannot learn a DMARC_RESULT_EQUALS rule: could not determine a From domain for this message"));
  }

  private Optional<String> evaluate(Message message) throws MessagingException {
    return evaluator.evaluate(message, getLogger()).map(e -> e.result().getCode());
  }
}
