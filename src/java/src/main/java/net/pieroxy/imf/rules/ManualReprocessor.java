package net.pieroxy.imf.rules;

import net.pieroxy.imf.utils.mail.ImapMailbox;
import net.pieroxy.imf.utils.MailTools;

import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Replays the rule catalog against messages dropped by hand into imf-rules/ToProcess (e.g. a
 * mail already sitting in INBOX that you want to run back through the rules, after adding or
 * fixing a rule that should have caught it). Each message there is treated exactly as if it had
 * just arrived: the first matching rule applies normally. If it's still in ToProcess once the
 * catalog is exhausted — whether no rule matched, or the one that did neither moved nor deleted
 * it — it's filed into imf-rules/Done (the same folder used by
 * {@link net.pieroxy.imf.learning.RuleLearner}), for the user to take a look at, since they're
 * the one who dropped the message there by hand.
 */
public class ManualReprocessor {
  private final static Logger LOGGER = Logger.getLogger(ManualReprocessor.class.getName());
  private final static String ROOT_FOLDER = "imf-rules";
  private final static String TO_PROCESS_FOLDER = "ToProcess";
  private final static String DONE_FOLDER = "Done";

  private final ImapMailbox mailbox;
  private final RuleCatalog ruleCatalog;

  public ManualReprocessor(ImapMailbox mailbox, RuleCatalog ruleCatalog) {
    this.mailbox = mailbox;
    this.ruleCatalog = ruleCatalog;
  }

  /** Creates imf-rules/ToProcess if needed, ready to receive messages dropped by hand. */
  public void ensureFolderSkeleton() throws MessagingException {
    mailbox.getOrCreateFolder(ROOT_FOLDER, TO_PROCESS_FOLDER);
  }

  public void reprocessPending() throws MessagingException {
    Folder toProcessFolder = mailbox.getOrCreateFolder(ROOT_FOLDER, TO_PROCESS_FOLDER);
    Message[] pending = mailbox.getAllMessages(toProcessFolder);
    // Silent when there's nothing to do (the normal case, every cycle): only log when a
    // manually-triggered action actually has something to show.
    if (pending.length > 0) {
      LOGGER.info(() -> pending.length + " message(s) found in " + ROOT_FOLDER + "/" + TO_PROCESS_FOLDER + " to reprocess");
    }
    try {
      for (Message message : pending) {
        reprocess(message);
      }
    } finally {
      mailbox.closeAndExpunge(toProcessFolder);
    }
  }

  private void reprocess(Message message) {
    try {
      LOGGER.info(() -> "Reprocessing message from " + MailTools.describeFromSafely(message));
      boolean matched = RuleHelper.processRules(ruleCatalog.get(), ruleCatalog.getLearnedRulesFallback(), message, LOGGER,
          ROOT_FOLDER + "/" + TO_PROCESS_FOLDER, ruleCatalog.getContext().statsDir());
      if (message.isSet(Flags.Flag.DELETED)) {
        LOGGER.info(() -> "Message from " + MailTools.describeFromSafely(message) + " was relocated by its matching rule's action");
        return;
      }
      // No rule matched, or the one that did didn't move/delete the message (e.g. an action
      // that just marks it): file it anyway so it isn't reprocessed in a loop every cycle.
      moveToDone(message);
      LOGGER.info(() -> (matched ? "Matching rule's action left the message in place; moved" : "No rule matched; moved")
              + " message from " + MailTools.describeFromSafely(message) + " to " + ROOT_FOLDER + "/" + DONE_FOLDER);
    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Failed to reprocess message from " + MailTools.describeFromSafely(message)
              + " under " + ROOT_FOLDER + "/" + TO_PROCESS_FOLDER, e);
    }
  }

  private void moveToDone(Message message) throws MessagingException {
    // Unread: copyMessages() below copies the message with its current flags, so \Seen must be
    // cleared beforehand to land on the copy — a clear "something here needs sorting" indicator
    // in the mail client, rather than a message silently waiting to be noticed.
    message.setFlag(Flags.Flag.SEEN, false);
    Folder doneFolder = mailbox.getOrCreateFolder(ROOT_FOLDER, DONE_FOLDER);
    message.getFolder().copyMessages(new Message[]{message}, doneFolder);
    message.setFlag(Flags.Flag.DELETED, true);
  }
}
