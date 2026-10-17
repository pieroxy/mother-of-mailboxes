package net.pieroxy.mom.rules;

import net.pieroxy.mom.config.general.MailAccountConfiguration;
import net.pieroxy.mom.config.general.MailFilterRuleConfiguration;
import net.pieroxy.mom.rules.actions.ActionType;
import net.pieroxy.mom.utils.mail.ImapMailboxConnection;
import net.pieroxy.mom.utils.mail.RecordingMailSender;
import org.junit.Test;

import javax.mail.Flags;
import javax.mail.Message;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class MailAccountForwardTest extends AbstractMailAccountTest {
  @Test
  public void forwardsOnceAndLeavesTheMessageInPlaceUnread() throws Exception {
    MailFilterRuleConfiguration rule = moveToSpamOnDomain("partner.example.com");
    rule.getAction().setType(ActionType.FORWARD);
    rule.getAction().setKey("other@example.net");
    MailAccountConfiguration config = fixture.accountConfig("test-account");
    config.setRules(List.of(rule));
    RecordingMailSender sender = new RecordingMailSender("me@example.com");
    MailAccount account = new MailAccount(config, fixture.accountCredential(), sender, tempFolder.getRoot().getAbsolutePath(),
        (c, credential) -> fixture.connectAsImapMailbox());
    account.processMessages(); // establishes the UID cursor before any mail is dropped

    fixture.appendMessage(messageFrom("bob@partner.example.com"), "INBOX");
    account.processMessages();
    account.processMessages();

    assertEquals(1, sender.sent.size());
    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      Message[] inbox = mailbox.getAllMessages(mailbox.getOrCreateFolder("INBOX"));
      assertEquals(1, inbox.length);
      assertFalse(inbox[0].isSet(Flags.Flag.SEEN));
    }
  }
}
