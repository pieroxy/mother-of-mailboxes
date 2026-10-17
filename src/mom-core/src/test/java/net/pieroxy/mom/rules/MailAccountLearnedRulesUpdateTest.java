package net.pieroxy.mom.rules;

import net.pieroxy.mom.config.general.MailFilterRuleConfiguration;
import net.pieroxy.mom.utils.mail.ImapMailboxConnection;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MailAccountLearnedRulesUpdateTest extends AbstractMailAccountTest {

  @Test
  public void getLearnedRulesReturnsWhatWasStored() throws Exception {
    MailAccount account = accountWith();
    account.updateLearnedRules(List.of(moveToSpamOnDomain("spammy.example.com")));

    List<MailFilterRuleConfiguration> reloaded = account.getLearnedRules();

    assertEquals(1, reloaded.size());
    assertEquals("spammy.example.com", reloaded.get(0).getMatcher().getKey());
  }

  /**
   * The whole point of a dedicated update path instead of the config.json restart flow: a
   * correction (e.g. removing a key wrongly learned) must take effect on the very next cycle, no
   * account restart needed.
   */
  @Test
  public void updateLearnedRulesTakesEffectWithoutARestart() throws Exception {
    MailAccount account = accountWith();
    account.processMessages(); // establishes the UID cursor before any mail is dropped

    account.updateLearnedRules(List.of(moveToSpamOnDomain("spammy.example.com")));

    fixture.appendMessage(messageFrom("someone@spammy.example.com"), "INBOX");
    account.processMessages();

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      assertEquals(1, mailbox.getAllMessages(mailbox.getOrCreateFolder("Spam")).length);
    }
  }

  @Test
  public void removingAKeyStopsItFromMatchingOnTheNextCycle() throws Exception {
    MailAccount account = accountWith();
    account.updateLearnedRules(List.of(moveToSpamOnDomain("spammy.example.com")));
    account.processMessages();

    account.updateLearnedRules(List.of()); // the correction: forget the learned domain entirely

    fixture.appendMessage(messageFrom("someone@spammy.example.com"), "INBOX");
    account.processMessages();

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      assertTrue("no learned rule left to match it: the message must stay in the INBOX",
          mailbox.getAllMessages(mailbox.getOrCreateFolder("INBOX")).length >= 1);
    }
  }
}
