package net.pieroxy.imf.utils.mail;

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import java.time.Instant;
import java.util.List;

/**
 * Abstraction over access to an IMAP account: decouples the rest of the code from connection
 * details (javax.mail Session/Store), and lets it be swapped for a fake in tests. Combines UID
 * tracking of the INBOX with generic access to the folder tree, used by rule learning
 * (imf-rules/).
 */
public interface ImapMailbox extends AutoCloseable {
  long getUidValidity() throws MessagingException;

  long getUidNext() throws MessagingException;

  /** INBOX messages whose UID is strictly greater than lastUid, sorted by ascending UID. */
  Message[] getMessagesSince(long lastUid) throws MessagingException;

  long getUid(Message message) throws MessagingException;

  /** Resolves the folder designated by this path (one segment per level), creating it if needed. */
  Folder getOrCreateFolder(String... pathSegments) throws MessagingException;

  /** Root of the account's folder tree, for generic enumeration. */
  Folder getRootFolder() throws MessagingException;

  /** Direct subfolders of parent, in the order returned by the server. */
  List<Folder> listSubfolders(Folder parent) throws MessagingException;

  /**
   * Generic variants (any folder, not just the INBOX) of the UID-tracking methods above, used
   * by the classifier corpus scan. Open the folder read-only if needed (never sets \Seen just
   * by walking it).
   */
  long getUidValidity(Folder folder) throws MessagingException;

  /**
   * @param maxResults caps how many messages this call fetches, not just how many it returns —
   *                    a folder with far more than that many unfetched messages (e.g. its very
   *                    first scan) still only costs one bounded batched fetch, not one sized to
   *                    the whole backlog.
   */
  Message[] getMessagesSince(Folder folder, long lastUid, int maxResults) throws MessagingException;

  long getUid(Folder folder, Message message) throws MessagingException;

  /**
   * UID immediately before the oldest message in folder whose INTERNALDATE is on/after cutoff —
   * i.e. the value to seed {@link #getMessagesSince(Folder, long, int)} with to start right at
   * the retention window's edge instead of the folder's very first message ever received. If
   * nothing in the folder is within the window, returns (UIDNEXT - 1): fully caught up, nothing
   * left to fetch there. Meant for a folder's first scan only (see ClassifierCorpusScanner) —
   * resolved server-side via IMAP SEARCH, so a folder with years of history outside the window
   * costs one cheap search instead of many batched fetches that would all get discarded anyway.
   */
  long lastUidBeforeCutoff(Folder folder, Instant cutoff) throws MessagingException;

  /** Closes folder without expunging (read-only, nothing to purge). */
  void closeReadOnly(Folder folder) throws MessagingException;

  /** All messages in folder (opens it read/write if needed). */
  Message[] getAllMessages(Folder folder) throws MessagingException;

  /** Closes folder, expunging messages marked \Deleted. */
  void closeAndExpunge(Folder folder) throws MessagingException;

  @Override
  void close();
}
