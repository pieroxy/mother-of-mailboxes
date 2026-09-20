package net.pieroxy.imf.detection.classifier;

import java.util.List;

/**
 * A record of the training corpus: whatever might feed a future classifier (subject, from/to
 * with their display name, best-effort originating IP, a few more headers —
 * In-Reply-To/References, Precedence, List-Id, List-Unsubscribe, Return-Path/Reply-To
 * consistency with From — plus the MIME structure's attachment filenames, the server-recorded
 * vs. self-reported send date, and the message body's visible text), labeled SPAM/HAM based on
 * the IMAP folder it came from.
 */
public class ClassifierExample {
  private String messageId;
  private String fetchDate;
  private String mailDate;
  private String receivedDate;
  private List<String> from;
  private String fromDisplayName;
  private List<String> to;
  private String toDisplayName;
  private String subject;
  private String ip;
  private boolean reply;
  private String precedence;
  private String listId;
  private boolean listUnsubscribePresent;
  private String returnPathDomain;
  private Boolean returnPathMismatch;
  private String replyToDomain;
  private Boolean replyToMismatch;
  private ClassifierLabel label;
  private List<String> attachmentExtensions;
  private String bodyText;
  private String bodySource;

  /**
   * Raw Message-ID header; null if absent (rare, malformed mail). Identifies the same message
   * across an IMAP move (which gives it a new UID in the destination folder, invisible to
   * UID-based tracking) — see {@code ClassifierCorpusStore#readAll()}, which deduplicates on it
   * to keep only the latest verdict for a message seen twice with contradictory labels (e.g.
   * auto-classified SPAM by the Spam folder scan, then moved out by hand by a user who judged it
   * legitimate).
   */
  public String getMessageId() {
    return messageId;
  }

  public void setMessageId(String messageId) {
    this.messageId = messageId;
  }

  public String getFetchDate() {
    return fetchDate;
  }

  public void setFetchDate(String fetchDate) {
    this.fetchDate = fetchDate;
  }

  public String getMailDate() {
    return mailDate;
  }

  public void setMailDate(String mailDate) {
    this.mailDate = mailDate;
  }

  /**
   * When the IMAP server itself recorded this message as received (INTERNALDATE) — set by the
   * server, not the sender, so unlike {@link #getMailDate()} (the self-reported {@code Date:}
   * header) it can't be forged by whoever sent the message. Compared against mailDate to catch a
   * sender's clock lying about when a message was sent (see {@link HeaderFeatureGenerator}).
   */
  public String getReceivedDate() {
    return receivedDate;
  }

  public void setReceivedDate(String receivedDate) {
    this.receivedDate = receivedDate;
  }

  public List<String> getFrom() {
    return from;
  }

  public void setFrom(List<String> from) {
    this.from = from;
  }

  public List<String> getTo() {
    return to;
  }

  public void setTo(List<String> to) {
    this.to = to;
  }

  /** Display name(s) of the sender(s) ("Alice" for "Alice &lt;alice@example.com&gt;"), joined by a space; null if none. */
  public String getFromDisplayName() {
    return fromDisplayName;
  }

  public void setFromDisplayName(String fromDisplayName) {
    this.fromDisplayName = fromDisplayName;
  }

  /** Display name(s) of the To recipient(s), joined by a space; null if none. */
  public String getToDisplayName() {
    return toDisplayName;
  }

  public void setToDisplayName(String toDisplayName) {
    this.toDisplayName = toDisplayName;
  }

  public String getSubject() {
    return subject;
  }

  public void setSubject(String subject) {
    this.subject = subject;
  }

  public String getIp() {
    return ip;
  }

  public void setIp(String ip) {
    this.ip = ip;
  }

  /** In-Reply-To or References present: a strong HAM signal, a genuine reply to an existing thread. */
  public boolean isReply() {
    return reply;
  }

  public void setReply(boolean reply) {
    this.reply = reply;
  }

  /** Raw value of the Precedence header ("bulk", "list", "junk"...); null if absent. Fixed vocabulary, unlike the other raw values below. */
  public String getPrecedence() {
    return precedence;
  }

  public void setPrecedence(String precedence) {
    this.precedence = precedence;
  }

  /** Raw value of the List-Id header; null if absent. Recurs from one mailing to the next for the same list, unlike a Message-ID. */
  public String getListId() {
    return listId;
  }

  public void setListId(String listId) {
    this.listId = listId;
  }

  public boolean isListUnsubscribePresent() {
    return listUnsubscribePresent;
  }

  public void setListUnsubscribePresent(boolean listUnsubscribePresent) {
    this.listUnsubscribePresent = listUnsubscribePresent;
  }

  /** Domain of Return-Path; null if absent or empty (a bounce with no return address, "&lt;&gt;"). */
  public String getReturnPathDomain() {
    return returnPathDomain;
  }

  public void setReturnPathDomain(String returnPathDomain) {
    this.returnPathDomain = returnPathDomain;
  }

  /** true if the Return-Path domain differs from the From domain; null if either one is undeterminable (not simply "no mismatch"). */
  public Boolean getReturnPathMismatch() {
    return returnPathMismatch;
  }

  public void setReturnPathMismatch(Boolean returnPathMismatch) {
    this.returnPathMismatch = returnPathMismatch;
  }

  /** Domain of the first Reply-To address; null if absent or malformed. */
  public String getReplyToDomain() {
    return replyToDomain;
  }

  public void setReplyToDomain(String replyToDomain) {
    this.replyToDomain = replyToDomain;
  }

  /** true if the Reply-To domain differs from the From domain; null if either one is undeterminable. */
  public Boolean getReplyToMismatch() {
    return replyToMismatch;
  }

  public void setReplyToMismatch(Boolean replyToMismatch) {
    this.replyToMismatch = replyToMismatch;
  }

  public ClassifierLabel getLabel() {
    return label;
  }

  public void setLabel(ClassifierLabel label) {
    this.label = label;
  }

  /**
   * Filename extension (lowercase, no dot) of each attachment-like MIME part found anywhere in
   * the message, one entry per attachment (duplicates possible, e.g. two ".pdf"s); empty if none.
   * The count is this list's size — no separate field, so the two can never drift apart.
   */
  public List<String> getAttachmentExtensions() {
    return attachmentExtensions;
  }

  public void setAttachmentExtensions(List<String> attachmentExtensions) {
    this.attachmentExtensions = attachmentExtensions;
  }

  /**
   * The message body's visible text: the HTML part's text (tags/scripts/styles stripped, see
   * {@link ClassifierExampleExtractor}) when one exists, since that's what a human actually sees
   * — a plain-text alternative part is normally just a fallback nobody reads and is ignored when
   * an HTML part is present; only used as-is (no stripping needed) when no HTML part exists at
   * all. Null if the message has neither. Not truncated: kept as long as it comes, on purpose —
   * revisit only once real corpus volume shows what length is actually reasonable.
   */
  public String getBodyText() {
    return bodyText;
  }

  public void setBodyText(String bodyText) {
    this.bodyText = bodyText;
  }

  /**
   * Which MIME part {@link #getBodyText()} came from: {@code "html"} or {@code "plain"}; null if
   * the message had neither (same condition as {@link #getBodyText()} being null). Kept as its
   * own field rather than inferred from {@code bodyText} being present, because stripped HTML
   * and a plain-text part look identical once reduced to text — this is exactly the distinction
   * that stripping erases, and a bag-of-words model over the text alone can't recover it (see
   * {@link BodyFeatureGenerator}).
   */
  public String getBodySource() {
    return bodySource;
  }

  public void setBodySource(String bodySource) {
    this.bodySource = bodySource;
  }
}
