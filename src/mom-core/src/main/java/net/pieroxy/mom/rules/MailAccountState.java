package net.pieroxy.mom.rules;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Persistent state of an account, used to process each message only once. uidValidity==0 means
 * "never initialized" (an IMAP server never returns 0).
 * <p>
 * The UID cursor alone isn't enough: a message manually moved back into a folder already scanned
 * (e.g. a false positive rescued from Spam back to INBOX) gets a fresh UID there, higher than the
 * cursor, and would otherwise be inspected — and re-matched, re-moved — all over again. See
 * {@link MailAccount#fingerprintOf}: a message that's already been through the rules once is
 * remembered by a fingerprint independent of its UID/folder, so it's left alone if seen again.
 */
public class MailAccountState {
  private long uidValidity;
  private long lastUid;
  private Map<String, String> processedFingerprints = new HashMap<>();

  public long getUidValidity() {
    return uidValidity;
  }

  public void setUidValidity(long uidValidity) {
    this.uidValidity = uidValidity;
  }

  public long getLastUid() {
    return lastUid;
  }

  public void setLastUid(long lastUid) {
    this.lastUid = lastUid;
  }

  public boolean isProcessed(String fingerprint) {
    return processedFingerprints.containsKey(fingerprint);
  }

  public void markProcessed(String fingerprint, LocalDate today) {
    processedFingerprints.put(fingerprint, today.toString());
  }

  /** Drops fingerprints first seen before {@code cutoff}, so this doesn't grow forever. */
  public void pruneProcessedFingerprintsBefore(LocalDate cutoff) {
    processedFingerprints.entrySet().removeIf(e -> LocalDate.parse(e.getValue()).isBefore(cutoff));
  }
}
