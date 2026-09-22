package net.pieroxy.mom.rules.matchers;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MatcherTypeTest {

  @Test
  public void fromMatchersAreLearnable() {
    assertTrue(MatcherType.learnableValues().contains(MatcherType.FROM_EQUALS));
    assertTrue(MatcherType.learnableValues().contains(MatcherType.FROM_ADDRESS_EQUALS));
    assertTrue(MatcherType.learnableValues().contains(MatcherType.FROM_DOMAIN_EQUALS));
    assertTrue(MatcherType.learnableValues().contains(MatcherType.SUBJECT_STARTS_WITH));
  }

  /**
   * Not learnable: unlike a literal address or domain, there's no sensible way to generalize a
   * regular expression from one example message.
   */
  @Test
  public void regexpFromAddressIsNotLearnable() {
    assertFalse(MatcherType.learnableValues().contains(MatcherType.FROM_ADDRESS_REGEXP));
  }

  @Test
  public void compositeTypesAreNotLearnable() {
    assertFalse(MatcherType.learnableValues().contains(MatcherType.AND));
    assertFalse(MatcherType.learnableValues().contains(MatcherType.OR));
    assertFalse(MatcherType.learnableValues().contains(MatcherType.NOT));
  }

  /**
   * SPF/DKIM/DMARC aren't learnable: their possible keys (pass/fail/softfail/...) are a fixed,
   * already-documented set, not a specific value to discover from the example — and unlike
   * FROM_*, the learned rule wouldn't be specific to the deposited example, it would apply
   * globally to any message with the same status.
   */
  @Test
  public void protocolResultMatchersAreNotLearnable() {
    assertFalse(MatcherType.learnableValues().contains(MatcherType.SPF_RESULT_EQUALS));
    assertFalse(MatcherType.learnableValues().contains(MatcherType.DKIM_RESULT_EQUALS));
    assertFalse(MatcherType.learnableValues().contains(MatcherType.DMARC_RESULT_EQUALS));
    assertFalse(MatcherType.learnableValues().contains(MatcherType.DMARC_POLICY_EQUALS));
    assertFalse(MatcherType.learnableValues().contains(MatcherType.FCRDNS_RESULT_EQUALS));
  }

  /**
   * Not learnable by dropped example either, but for a different reason than the protocol
   * matchers above: the learning comes from the classifier corpus (see
   * SubjectClassifierTrainer), not from mom-rules/ folders.
   */
  @Test
  public void subjectClassifierIsNotLearnable() {
    assertFalse(MatcherType.learnableValues().contains(MatcherType.SUBJECT_CLASSIFIER_EQUALS));
    assertFalse(MatcherType.learnableValues().contains(MatcherType.HEADER_CLASSIFIER_EQUALS));
    assertFalse(MatcherType.learnableValues().contains(MatcherType.BODY_CLASSIFIER_EQUALS));
  }

  /**
   * Not learnable either: reputation comes from downloaded external lists (see
   * net.pieroxy.mom.reputation.ReputationRegistry), not from an example dropped in mom-rules/.
   */
  @Test
  public void reputationMatchersAreNotLearnable() {
    assertFalse(MatcherType.learnableValues().contains(MatcherType.IP_REPUTATION_EQUALS));
    assertFalse(MatcherType.learnableValues().contains(MatcherType.FROM_DOMAIN_REPUTATION_EQUALS));
  }
}
