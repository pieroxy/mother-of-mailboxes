package net.pieroxy.mom.utils.mail;

import com.sun.mail.imap.IMAPFolder;
import net.pieroxy.mom.config.credentials.Credential;
import net.pieroxy.mom.config.general.MailAccountConfiguration;

import javax.mail.*;
import javax.mail.search.ComparisonTerm;
import javax.mail.search.ReceivedDateTerm;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link ImapMailbox} implementation based on javax.mail. The only class in the project that
 * knows the low-level IMAP connection details.
 */
public class ImapMailboxConnection implements ImapMailbox {
  private final static Logger LOGGER = Logger.getLogger(ImapMailboxConnection.class.getName());

  private final Store store;
  private final IMAPFolder inbox;

  private ImapMailboxConnection(Store store, IMAPFolder inbox) {
    this.store = store;
    this.inbox = inbox;
  }

  /**
   * Connects to the account and opens the INBOX in read/write mode (actions need to be able to
   * modify messages). The caller is responsible for closing the connection, ideally via a
   * try-with-resources.
   */
  public static ImapMailboxConnection connect(MailAccountConfiguration config, Credential credential) throws MessagingException {
    Session session = Session.getDefaultInstance(peekProperties());
    session.setDebug(false);
    Store store = session.getStore("imaps");
    store.connect(config.getHost(), config.getPort(), credential.getUsername(), credential.getPassword());
    IMAPFolder inbox = (IMAPFolder) store.getFolder("INBOX");
    inbox.open(Folder.READ_WRITE);
    return new ImapMailboxConnection(store, inbox);
  }

  /**
   * Reserved for tests: wraps an already-connected Store (e.g. a GreenMail server over plain
   * IMAP) without going through connect()/IMAPS — lets the rest of this class be tested without
   * TLS or a real remote IMAP server.
   */
  public static ImapMailboxConnection forTesting(Store connectedStore) throws MessagingException {
    IMAPFolder inbox = (IMAPFolder) connectedStore.getFolder("INBOX");
    inbox.open(Folder.READ_WRITE);
    return new ImapMailboxConnection(connectedStore, inbox);
  }

  @Override
  public long getUidValidity() throws MessagingException {
    return inbox.getUIDValidity();
  }

  @Override
  public long getUidNext() throws MessagingException {
    return inbox.getUIDNext();
  }

  @Override
  public Message[] getMessagesSince(long lastUid) throws MessagingException {
    Message[] candidates = inbox.getMessagesByUID(lastUid + 1, UIDFolder.LASTUID);
    List<Message> result = new ArrayList<>();
    for (Message m : candidates) {
      // getMessagesByUID can return the lower bound even if its UID is actually <= lastUid.
      if (inbox.getUID(m) > lastUid) result.add(m);
    }
    return result.toArray(new Message[0]);
  }

  @Override
  public long getUid(Message message) throws MessagingException {
    return inbox.getUID(message);
  }

  @Override
  public Folder getOrCreateFolder(String... pathSegments) throws MessagingException {
    // Relative navigation, level by level: independent of the server's separator character
    // (unlike a fully-qualified name passed directly to store.getFolder(...)).
    Folder current = store.getDefaultFolder();
    for (String segment : pathSegments) {
      current = current.getFolder(segment);
      if (!current.exists()) {
        current.create(Folder.HOLDS_MESSAGES | Folder.HOLDS_FOLDERS);
      }
    }
    return current;
  }

  @Override
  public Folder getRootFolder() throws MessagingException {
    return store.getDefaultFolder();
  }

  @Override
  public List<Folder> listSubfolders(Folder parent) throws MessagingException {
    return Arrays.asList(parent.list());
  }

  @Override
  public long getUidValidity(Folder folder) throws MessagingException {
    openReadOnly(folder);
    return ((IMAPFolder) folder).getUIDValidity();
  }

  @Override
  public Message[] getMessagesSince(Folder folder, long lastUid, int maxResults) throws MessagingException {
    openReadOnly(folder);
    IMAPFolder imapFolder = (IMAPFolder) folder;
    Message[] candidates = imapFolder.getMessagesByUID(lastUid + 1, UIDFolder.LASTUID);
    List<Message> result = new ArrayList<>();
    for (Message m : candidates) {
      // Same as for the INBOX: getMessagesByUID can return the lower bound even if its UID is
      // actually <= lastUid.
      if (imapFolder.getUID(m) > lastUid) {
        result.add(m);
        // Capped here, before the batched fetch below: on a folder with years of unfetched
        // history, that fetch (envelope + MIME structure for every candidate) is the expensive
        // part, not the cheap UID resolution above — capping only the processing loop
        // afterward wouldn't stop a single call from still trying to fetch everything at once.
        if (result.size() >= maxResults) break;
      }
    }
    Message[] messages = result.toArray(new Message[0]);
    // Without this batched fetch, every individual access to a header (Subject/From/To/Date/
    // Received/Precedence/List-Id/...) or to the MIME structure (attachment filenames, for the
    // classifier corpus) triggers its own IMAP round trip per message: on a folder with several
    // thousand messages, that's thousands of extra round trips — cheap enough on a fast loopback
    // to go unnoticed, but genuinely slow wherever it isn't. HEADERS (not individually-named
    // items like "Received") is used deliberately: ClassifierExampleExtractor reads a specific
    // set of header names today, but naming each one here would silently need updating every
    // time that set changes — HEADERS fetches all of them in this one batch regardless, via
    // BODY.PEEK[HEADER] (never marks \Seen, same as the individually-named items it replaces).
    if (messages.length > 0) {
      FetchProfile profile = new FetchProfile();
      profile.add(FetchProfile.Item.ENVELOPE);
      profile.add(FetchProfile.Item.CONTENT_INFO);
      profile.add(IMAPFolder.FetchProfileItem.HEADERS);
      // getReceivedDate() (INTERNALDATE) is its own IMAP attribute, not part of HEADERS —
      // without prefetching it too, every message would cost one more individual round trip,
      // exactly the mistake this batched fetch exists to avoid.
      profile.add(IMAPFolder.FetchProfileItem.INTERNALDATE);
      folder.fetch(messages, profile);
    }
    return messages;
  }

  @Override
  public long getUid(Folder folder, Message message) throws MessagingException {
    return ((IMAPFolder) folder).getUID(message);
  }

  @Override
  public long lastUidBeforeCutoff(Folder folder, Instant cutoff) throws MessagingException {
    openReadOnly(folder);
    IMAPFolder imapFolder = (IMAPFolder) folder;
    // SINCE compares against INTERNALDATE, date-only (no time-of-day) — same attribute
    // ClassifierCorpusScanner's own per-message age filter uses (getReceivedDate()), just
    // coarser; that filter still runs on whatever this returns, so the extra day of slack here
    // is harmless.
    Message[] matches = folder.search(new ReceivedDateTerm(ComparisonTerm.GE, Date.from(cutoff)));
    if (matches.length == 0) {
      // Nothing in the folder is recent enough: skip past all of it in one shot rather than
      // crawling through years of history that would only get discarded message by message.
      return imapFolder.getUIDNext() - 1;
    }
    long minUid = Long.MAX_VALUE;
    for (Message m : matches) {
      minUid = Math.min(minUid, imapFolder.getUID(m));
    }
    return minUid - 1;
  }

  @Override
  public void closeReadOnly(Folder folder) throws MessagingException {
    if (folder.isOpen()) folder.close(false);
  }

  private void openReadOnly(Folder folder) throws MessagingException {
    if (!folder.isOpen()) folder.open(Folder.READ_ONLY);
  }

  @Override
  public Message[] getAllMessages(Folder folder) throws MessagingException {
    if (!folder.isOpen()) folder.open(Folder.READ_WRITE);
    return folder.getMessages();
  }

  @Override
  public void closeAndExpunge(Folder folder) throws MessagingException {
    if (folder.isOpen()) folder.close(true);
  }

  @Override
  public void close() {
    try {
      // expunge=true: actions (MoveToAction) mark \Deleted the messages they relocate elsewhere;
      // the INBOX needs to be purged accordingly at the end of the cycle.
      if (inbox.isOpen()) inbox.close(true);
    } catch (MessagingException e) {
      LOGGER.log(Level.WARNING, "Error closing inbox", e);
    }
    try {
      if (store.isConnected()) store.close();
    } catch (MessagingException e) {
      LOGGER.log(Level.WARNING, "Error closing store", e);
    }
  }

  /**
   * A safety net for the automatic prefetch (FetchProfile) that a future call might trigger —
   * but beware, this is NOT enough for the case that actually matters today: reading a
   * message's full content on demand (message.writeTo(), used by DkimResultMatcher/
   * DmarcResultMatcher to verify a DKIM signature) ignores this property (verified empirically
   * with an IMAP trace: javax.mail still sends BODY[], not BODY.PEEK[], despite "peek"=true
   * here). The real fix for that path is {@code IMAPMessage.setPeek(true)} set per message
   * before reading, see {@link net.pieroxy.mom.utils.MailTools#readRawMessageWithoutMarkingSeen}.
   * The two properties below exist because it's set per protocol ("imap" plaintext vs "imaps").
   */
  private static Properties peekProperties() {
    Properties props = new Properties();
    props.setProperty("mail.imap.peek", "true");
    props.setProperty("mail.imaps.peek", "true");
    return props;
  }
}
