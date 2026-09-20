package net.pieroxy.imf.detection.reputation;

/** A list loaded and ready to query — see {@link IpReputationList}/{@link DomainReputationList}. */
interface ReputationList {
  boolean contains(String value);
}
