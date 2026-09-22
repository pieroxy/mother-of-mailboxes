package net.pieroxy.mom.detection.reputation;

/**
 * A value found in one of the referenced reputation lists: which list ({@code listId}) and the
 * score it carries — {@link ReputationRegistry#ipScore}/{@link ReputationRegistry#domainScore}
 * return the worst one across every list a matcher references, and callers want to know which
 * list that was (for logs — see {@code IpReputationMatcher}/{@code FromDomainReputationMatcher}),
 * not just the score itself.
 */
public record ReputationMatch(String listId, double score) {
}
