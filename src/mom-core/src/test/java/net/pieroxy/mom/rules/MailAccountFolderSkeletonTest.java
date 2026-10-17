package net.pieroxy.mom.rules;

import net.pieroxy.mom.utils.mail.ImapMailboxConnection;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class MailAccountFolderSkeletonTest extends AbstractMailAccountTest {
  @Test
  public void ensuresTheMomRulesFolderSkeletonOnFirstCycle() throws Exception {
    accountWith().processMessages();

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      assertTrue(mailbox.getOrCreateFolder("mom-rules", "ToProcess").exists());
      assertTrue(mailbox.getOrCreateFolder("mom-rules", "Done").exists());
    }
  }
}
