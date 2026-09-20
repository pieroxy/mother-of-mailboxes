package net.pieroxy.imf.rules;

import net.pieroxy.imf.utils.mail.ImapMailboxConnection;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MailAccountSecondCycleTest extends AbstractMailAccountTest {
  @Test
  public void aSecondCycleDoesNotReapplyRulesToAlreadyProcessedMail() throws Exception {
    MailAccount account = accountWith(moveToSpamOnDomain("spammy.example.com"));
    account.processMessages(); // establishes the UID cursor before any mail is dropped

    fixture.appendMessage(messageFrom("first@spammy.example.com"), "INBOX");
    account.processMessages();
    fixture.appendMessage(messageFrom("second@spammy.example.com"), "INBOX");
    account.processMessages();

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      // Both spammy messages were moved: one per cycle, never reprocessed twice.
      assertEquals(2, mailbox.getAllMessages(mailbox.getOrCreateFolder("Spam")).length);
    }
  }
}
