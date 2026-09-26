package net.pieroxy.mom.rules.matchers;

import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.rules.matchers.implementations.AndMatcher;
import net.pieroxy.mom.rules.matchers.implementations.BodyClassifierMatcher;
import net.pieroxy.mom.rules.matchers.implementations.DkimResultMatcher;
import net.pieroxy.mom.rules.matchers.implementations.DmarcPolicyMatcher;
import net.pieroxy.mom.rules.matchers.implementations.DmarcResultMatcher;
import net.pieroxy.mom.rules.matchers.implementations.FcrdnsResultMatcher;
import net.pieroxy.mom.rules.matchers.implementations.FromAddressMatcher;
import net.pieroxy.mom.rules.matchers.implementations.FromDomainMatcher;
import net.pieroxy.mom.rules.matchers.implementations.FromDomainReputationMatcher;
import net.pieroxy.mom.rules.matchers.implementations.FromExactMatcher;
import net.pieroxy.mom.rules.matchers.implementations.HeaderClassifierMatcher;
import net.pieroxy.mom.rules.matchers.implementations.IpReputationMatcher;
import net.pieroxy.mom.rules.matchers.implementations.NotMatcher;
import net.pieroxy.mom.rules.matchers.implementations.OrMatcher;
import net.pieroxy.mom.rules.matchers.implementations.RegexpFromAddressMatcher;
import net.pieroxy.mom.rules.matchers.implementations.SpfResultMatcher;
import net.pieroxy.mom.rules.matchers.implementations.SubjectClassifierMatcher;
import net.pieroxy.mom.rules.matchers.implementations.SubjectStartsWithMatcher;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@TypeScriptType
public enum MatcherType {
  FROM_EQUALS(FromExactMatcher::new, true),
  FROM_ADDRESS_EQUALS(FromAddressMatcher::new, true),
  FROM_DOMAIN_EQUALS(FromDomainMatcher::new, true),
  // Not learnable: unlike a literal address or domain, there's no sensible way to generalize a
  // regular expression from one example message.
  FROM_ADDRESS_REGEXP(RegexpFromAddressMatcher::new, false),
  SUBJECT_STARTS_WITH(SubjectStartsWithMatcher::new, true),
  // Not learnable: the possible key (pass/fail/softfail/...) is a fixed, already-documented
  // set, not a specific value to discover from the example. And unlike FROM_*, the learned
  // rule wouldn't be specific to the deposited example: it would apply globally to any message
  // with the same status, not just to one particular sender.
  SPF_RESULT_EQUALS(SpfResultMatcher::new, false),
  DKIM_RESULT_EQUALS(DkimResultMatcher::new, false),
  DMARC_RESULT_EQUALS(DmarcResultMatcher::new, false),
  DMARC_POLICY_EQUALS(DmarcPolicyMatcher::new, false),
  FCRDNS_RESULT_EQUALS(FcrdnsResultMatcher::new, false),
  // Not learnable either, but for a different reason than the authentication matchers above:
  // learning here doesn't come from an example dropped in mom-rules/, but from the corpus
  // collected by ClassifierCorpusScanner and retrained by SubjectClassifierTrainer.
  SUBJECT_CLASSIFIER_EQUALS(SubjectClassifierMatcher::new, false),
  // Same reasoning as SUBJECT_CLASSIFIER_EQUALS, different corpus features (headers, not the
  // subject text) and a different underlying model (Maxent on structured features rather than
  // Naive Bayes bag-of-words) — see HeaderClassifierTrainer/HeaderFeatureGenerator.
  HEADER_CLASSIFIER_EQUALS(HeaderClassifierMatcher::new, false),
  // Same reasoning again, this time on the body's visible text (HTML stripped down to text) —
  // see BodyClassifierTrainer/BodyFeatureGenerator.
  BODY_CLASSIFIER_EQUALS(BodyClassifierMatcher::new, false),
  // Not learnable either: reputation comes from downloaded external lists (see
  // net.pieroxy.mom.reputation.ReputationRegistry), not from an example dropped in mom-rules/.
  IP_REPUTATION_EQUALS(IpReputationMatcher::new, false),
  FROM_DOMAIN_REPUTATION_EQUALS(FromDomainReputationMatcher::new, false),
  AND(AndMatcher::new, false),
  OR(OrMatcher::new, false),
  // Not learnable, same reasoning as AND/OR: reserved for manual config.json rules.
  NOT(NotMatcher::new, false);

  private final MatcherProvider provider;
  private final boolean learnable;

  MatcherType(MatcherProvider provider, boolean learnable) {
    this.provider = provider;
    this.learnable = learnable;
  }

  public Matcher getImplementation() {
    return provider.getMatcher();
  }

  /**
   * "Leaf" types for which rule learning by example (mom-rules/ folders) makes sense.
   * Composites (AND/OR) are excluded: reserved for manual config.
   */
  public static List<MatcherType> learnableValues() {
    return Arrays.stream(values()).filter(t -> t.learnable).collect(Collectors.toList());
  }
}

interface MatcherProvider {
  Matcher getMatcher();
}