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
 * Compares the sender domain's effective DMARC policy against the configured key: {@code none},
 * {@code quarantine}, {@code reject} (RFC 7489 values of the {@code p=}/{@code sp=} tag),
 * {@code unpublished} (the domain has no DMARC at all), {@code permerror}, or {@code temperror}.
 * Case-insensitive.
 * <p>
 * Unlike {@link DmarcResultMatcher}, this matcher says nothing about the specific message being
 * evaluated — just about what the sender domain itself has published as its policy. Useful to
 * grade confidence: a {@code DMARC_RESULT_EQUALS: fail} coming from a domain with {@code p=reject}
 * is a near-certain spoofing signal (the domain committed to being strict), whereas the same
 * {@code fail} from a domain with {@code p=none} might just reflect an incomplete SPF/DKIM
 * deployment on the sender's side — combine both via {@code AND} to tell the two cases apart
 * instead of treating every {@code fail} the same way.
 * <p>
 * {@code unpublished} deliberately doesn't carry the same meaning as {@code none}: a domain with
 * no DMARC at all isn't inherently suspicious (it's the norm for most small domains), whereas
 * {@code p=none} is an active choice made by the domain.
 */
public class DmarcPolicyMatcher extends Matcher {
  private final DmarcMessageEvaluator evaluator;

  public DmarcPolicyMatcher() {
    this(new DmarcMessageEvaluator(new SpfEvaluator(new DnsJavaSpfDnsResolver()),
            DkimVerifier.createDefault(),
            new DmarcEvaluator(new DnsJavaDmarcDnsResolver())));
  }

  /** Visible for tests: allows injecting an evaluator with no network access. */
  DmarcPolicyMatcher(DmarcMessageEvaluator evaluator) {
    this.evaluator = evaluator;
  }

  @Override
  public MatchResult matches(Message message) throws MessagingException {
    Optional<String> policy = evaluate(message);
    Optional<String> hit = policy.flatMap(p -> matchingKey(p, String::equalsIgnoreCase));
    getLogger().fine(() -> "tested dmarc policy=" + policy.orElse(null) + " against " + describeKey()
            + " -> " + (hit.isPresent() ? "match" : "no match"));
    return hit.map(this::matched).orElseGet(this::notMatched);
  }

  @Override
  public String extractKeyFromExample(Message message) throws MessagingException {
    return evaluate(message).orElseThrow(() ->
            new MessagingException("Cannot learn a DMARC_POLICY_EQUALS rule: could not determine a From domain for this message"));
  }

  private Optional<String> evaluate(Message message) throws MessagingException {
    return evaluator.evaluate(message, getLogger()).map(e -> e.policy().getCode());
  }
}
