package net.pieroxy.mom.detection.dmarc;

/**
 * Permanent DMARC evaluation error: a malformed record (no {@code p=} tag) or an ambiguous one
 * (several valid TXT records at the same name, RFC 7489 §6.6.3 — the receiver has no way to know
 * which is authoritative). Translates to {@link DmarcResult#PERMERROR}.
 */
class DmarcPermErrorException extends Exception {
  DmarcPermErrorException(String message) {
    super(message);
  }
}
