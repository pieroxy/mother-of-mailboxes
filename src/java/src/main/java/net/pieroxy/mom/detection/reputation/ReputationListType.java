package net.pieroxy.mom.detection.reputation;

/**
 * Format of a reputation list, one entry per line ("#" comments, blank lines ignored): either
 * IPv4/CIDR ({@code IP_CIDR}) or domain names ({@code DOMAIN}). Determines both which parser to
 * use ({@link ReputationListParser}) and which matcher can reference the list
 * ({@code IP_REPUTATION_EQUALS} for IP_CIDR, {@code FROM_DOMAIN_REPUTATION_EQUALS} for DOMAIN) —
 * see {@link ReputationRegistry}.
 */
public enum ReputationListType {
  IP_CIDR,
  DOMAIN
}
