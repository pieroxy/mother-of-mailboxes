package net.pieroxy.mom.api.implementations.accountfolders;

import net.pieroxy.mom.utils.mail.GreenMailImapFixture;
import net.pieroxy.mom.utils.mail.ImapMailboxConnection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.mail.Folder;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AccountFoldersApiTest {
  private final GreenMailImapFixture fixture = new GreenMailImapFixture();

  @Before
  public void startServer() {
    fixture.start();
  }

  @After
  public void stopServer() {
    fixture.stop();
  }

  @Test
  public void listsFoldersIncludingNestedOnes() throws Exception {
    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      Folder archive = mailbox.getOrCreateFolder("Archive");
      Folder spam = mailbox.getOrCreateFolder("Spam");
      Folder nested = mailbox.getOrCreateFolder("Work", "Clients");

      List<String> folders = AccountFoldersApi.listFolders(mailbox);

      assertTrue(folders.contains(archive.getFullName()));
      assertTrue(folders.contains(spam.getFullName()));
      assertTrue("a nested folder must be listed too, by its full path", folders.contains(nested.getFullName()));
    }
  }

  @Test
  public void excludesInboxAndTheLearningShortcutsRoot() throws Exception {
    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      mailbox.getOrCreateFolder("Archive");
      mailbox.getOrCreateFolder("mom-rules", "FROM_DOMAIN_EQUALS", "spam.example.com");

      List<String> folders = AccountFoldersApi.listFolders(mailbox);

      assertTrue(folders.contains("Archive"));
      assertFalse("INBOX must never be offered as a move-to destination", folders.stream().anyMatch(f -> f.equalsIgnoreCase("INBOX")));
      assertFalse("mom-rules and its learned-example subfolders must be excluded",
          folders.stream().anyMatch(f -> f.toLowerCase().contains("mom-rules")));
    }
  }
}
