package net.pieroxy.imf.detection.dmarc;

/**
 * Detailed result of a DMARC evaluation: the pass/fail/none/... verdict (see {@link DmarcResult},
 * used by {@code DMARC_RESULT_EQUALS}), and the policy published by the domain (see
 * {@link DmarcPolicy}, used by {@code DMARC_POLICY_EQUALS}) — both derived from the same DNS
 * record, computed in a single pass by {@link DmarcEvaluator#evaluateDetailed}.
 */
public record DmarcEvaluation(DmarcResult result, DmarcPolicy policy) {
}
