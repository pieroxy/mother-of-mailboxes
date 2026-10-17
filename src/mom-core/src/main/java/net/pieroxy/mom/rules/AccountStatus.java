package net.pieroxy.mom.rules;

/** Live processing state of a {@link MailAccount}, exposed via the web API for the dashboard. */
public enum AccountStatus {
  /** Idle between cycles: waiting for new mail (IMAP IDLE) or the next scheduled poll. */
  OK,
  /** The account's last cycle failed (e.g. couldn't connect). */
  KO,
  /** A cycle is currently running. */
  PROCESSING
}
