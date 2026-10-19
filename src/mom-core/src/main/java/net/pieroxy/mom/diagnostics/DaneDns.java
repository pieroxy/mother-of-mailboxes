package net.pieroxy.mom.diagnostics;

import java.util.List;

/**
 * DNSSEC-validated DNS lookups for {@link DaneCheck}: DANE records only count when DNSSEC
 * vouches for them (RFC 7672 §2.2), so every answer comes with its validation status. An
 * interface so the check can be tested with in-memory answers.
 */
public interface DaneDns {
  enum Security {
    /** Signed, and the signatures check out. */
    SECURE,
    /** Provably not signed. */
    INSECURE,
    /** Signed, but the signatures don't check out: validating resolvers refuse the answer. */
    BOGUS,
  }

  /** {@code reason}: why it's insecure or bogus, as the validator explains it (may be null). */
  record Answer<T>(List<T> records, Security security, String reason) {}

  /** A TLSA record (RFC 6698 §2.1). */
  record Tlsa(int usage, int selector, int matchingType, byte[] data) {}

  /** MX host names, without their trailing dot. */
  Answer<String> mx(String domain) throws DnsFailure;

  Answer<Tlsa> tlsa(String name) throws DnsFailure;

  /** Which resolver answers — e.g. "the system's", or the public fallback and why. */
  String describe();

  class DnsFailure extends Exception {
    public DnsFailure(String message) {
      super(message);
    }
  }
}
