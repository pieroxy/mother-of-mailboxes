package net.pieroxy.imf.rules;

import net.pieroxy.imf.utils.mail.ImapMailboxConnection;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class MailAccountFolderSkeletonTest extends AbstractMailAccountTest {
  @Test
  public void ensuresTheImfRulesFolderSkeletonOnFirstCycle() throws Exception {
    accountWith().processMessages();

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      assertTrue(mailbox.getOrCreateFolder("imf-rules", "ToProcess").exists());
      assertTrue(mailbox.getOrCreateFolder("imf-rules", "Done").exists());
    }
  }
}
