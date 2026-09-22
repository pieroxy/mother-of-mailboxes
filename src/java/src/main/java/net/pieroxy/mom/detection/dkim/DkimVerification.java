package net.pieroxy.mom.detection.dkim;

import java.util.List;

/**
 * Detailed result of a DKIM verification: the aggregate result (see {@link DkimVerifier}), plus
 * the domains ({@code d=}) of every signature that actually verified successfully. This detail
 * feeds a DMARC's DKIM alignment computation (RFC 7489 §3.1.2), which needs to know *which*
 * domain signed, not just whether one succeeded.
 */
public record DkimVerification(DkimResult result, List<String> passingDomains) {
}
