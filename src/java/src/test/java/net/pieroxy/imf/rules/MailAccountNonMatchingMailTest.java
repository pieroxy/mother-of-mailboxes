package net.pieroxy.imf.rules;

import net.pieroxy.imf.utils.mail.ImapMailboxConnection;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MailAccountNonMatchingMailTest extends AbstractMailAccountTest {
  @Test
  public void nonMatchingMailStaysInInbox() throws Exception {
    MailAccount account = accountWith(moveToSpamOnDomain("spammy.example.com"));
    account.processMessages();

    fixture.appendMessage(messageFrom("sender@unrelated.example.com"), "INBOX");
    account.processMessages();

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      assertEquals(1, mailbox.getAllMessages(mailbox.getOrCreateFolder("INBOX")).length);
    }
  }
}
