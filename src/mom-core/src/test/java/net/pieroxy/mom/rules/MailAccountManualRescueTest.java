package net.pieroxy.mom.rules;

import net.pieroxy.mom.utils.mail.ImapMailboxConnection;
import org.junit.Test;

import javax.mail.Folder;
import javax.mail.Message;

import static org.junit.Assert.assertEquals;

public class MailAccountManualRescueTest extends AbstractMailAccountTest {
  /**
   * The false-positive-rescue scenario this whole mechanism exists for: a message matched a rule
   * and got moved to Spam; the user, noticing it's not actually spam, drags it back into INBOX by
   * hand (an IMAP COPY into INBOX + delete from Spam, same as any mail client). Without the
   * fingerprint check, the rescued copy would show up under a fresh UID above the cursor, get
   * inspected as if brand new, match the same rule again, and get moved straight back to Spam.
   */
  @Test
  public void aMessageManuallyRescuedFromSpamIsNotReMovedThere() throws Exception {
    MailAccount account = accountWith(moveToSpamOnDomain("spammy.example.com"));
    account.processMessages(); // establishes the UID cursor before any mail is dropped

    fixture.appendMessage(messageFrom("first@spammy.example.com"), "INBOX");
    account.processMessages(); // moves it to Spam

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      assertEquals(1, mailbox.getAllMessages(mailbox.getOrCreateFolder("Spam")).length);
      assertEquals(0, mailbox.getAllMessages(mailbox.getOrCreateFolder("INBOX")).length);

      // Simulate the user's manual rescue: a real IMAP COPY from Spam back into INBOX (same
      // operation any mail client performs when you drag a message between folders), followed by
      // deleting the original — exactly what preserves flags/internal date per RFC 3501 §6.4.7.
      Folder spam = mailbox.getOrCreateFolder("Spam");
      spam.open(Folder.READ_WRITE);
      Message[] toRescue = spam.getMessages();
      spam.copyMessages(toRescue, mailbox.getOrCreateFolder("INBOX"));
      spam.setFlags(toRescue, new javax.mail.Flags(javax.mail.Flags.Flag.DELETED), true);
      spam.close(true);
    }

    account.processMessages(); // must NOT re-match and re-move the rescued copy

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      assertEquals("rescued message must stay in INBOX, not bounce back to Spam",
          1, mailbox.getAllMessages(mailbox.getOrCreateFolder("INBOX")).length);
      assertEquals("no new copy should have landed in Spam",
          0, mailbox.getAllMessages(mailbox.getOrCreateFolder("Spam")).length);
    }
  }
}
