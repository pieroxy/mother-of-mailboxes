package net.pieroxy.mom.detection.dmarc;

import java.util.List;

/**
 * Abstraction of the DNS queries {@link DmarcEvaluator} needs: TXT lookups only (the DMARC
 * record lives at {@code _dmarc.<domain>}). Lets the algorithm be tested without a network.
 * An empty list means "no record at that name," not an error; {@link DmarcDnsException} is
 * reserved for temporary failures (timeout, SERVFAIL...).
 */
public interface DmarcDnsResolver {
  List<String> lookupTxt(String name) throws DmarcDnsException;
}
