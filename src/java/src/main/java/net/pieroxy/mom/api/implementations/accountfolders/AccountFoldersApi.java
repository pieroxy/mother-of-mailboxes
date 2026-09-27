package net.pieroxy.mom.api.implementations.accountfolders;

import net.pieroxy.mom.api.ServiceProvider;
import net.pieroxy.mom.api.metadata.AbstractApiEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.rules.MailAccount;
import net.pieroxy.mom.utils.mail.ImapMailbox;
import net.pieroxy.mom.utils.mail.ImapMailboxConnection;

import javax.mail.Folder;
import javax.mail.MessagingException;
import java.util.ArrayList;
import java.util.List;

/**
 * Lists an account's IMAP folders (full names), for the webapp's RuleEditPage to offer as an
 * editable dropdown when picking a MOVE_TO/MOVE_TO_AND_READ destination — fewer typos than free
 * text, while a datalist still lets the user type a name that doesn't exist yet. INBOX and the
 * learning-shortcuts root ("mom-rules") are excluded, same convention as
 * {@code ClassifierCorpusScanner}: neither is a sensible move-to destination.
 * <p>
 * Opens a short-lived, dedicated connection purely to walk the folder tree, then closes it — this
 * process never holds onto an account's connection between processing cycles, so there's no
 * existing one to reuse.
 */
@Endpoint(method = ApiMethod.GET)
public class AccountFoldersApi extends AbstractApiEndpoint<AccountFoldersApiInput, AccountFoldersApiOutput> {
  private final ServiceProvider serviceProvider;

  public AccountFoldersApi(ServiceProvider serviceProvider) {
    this.serviceProvider = serviceProvider;
  }

  @Override
  public AccountFoldersApiOutput process(AccountFoldersApiInput input) throws MessagingException {
    MailAccount account = serviceProvider.getAccounts().stream()
        .filter(a -> a.getAccountLabel().equals(input.getAccountName()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("No such account: " + input.getAccountName()));

    try (ImapMailboxConnection mailbox = ImapMailboxConnection.connect(account.getConfig(), account.getCredential())) {
      return new AccountFoldersApiOutput(listFolders(mailbox));
    }
  }

  /** Package-visible for tests: the actual walk, independent of how the connection was opened. */
  static List<String> listFolders(ImapMailbox mailbox) throws MessagingException {
    List<String> folders = new ArrayList<>();
    collect(mailbox, mailbox.getRootFolder(), folders);
    return folders;
  }

  private static void collect(ImapMailbox mailbox, Folder parent, List<String> out) throws MessagingException {
    for (Folder folder : mailbox.listSubfolders(parent)) {
      String name = folder.getName();
      // Skip both listing AND recursing into these — mom-rules/ in particular holds one
      // subfolder per learned example, none of which are sensible move-to destinations.
      if ("INBOX".equalsIgnoreCase(name) || "mom-rules".equalsIgnoreCase(name)) continue;

      int type = folder.getType();
      if ((type & Folder.HOLDS_MESSAGES) != 0) {
        out.add(folder.getFullName());
      }
      if ((type & Folder.HOLDS_FOLDERS) != 0) {
        collect(mailbox, folder, out);
      }
    }
  }
}

@TypeScriptType
class AccountFoldersApiInput {
  private String accountName;

  public String getAccountName() {
    return accountName;
  }

  public void setAccountName(String accountName) {
    this.accountName = accountName;
  }
}

@TypeScriptType
class AccountFoldersApiOutput {
  private List<String> folders;

  public AccountFoldersApiOutput() {
  }

  public AccountFoldersApiOutput(List<String> folders) {
    this.folders = folders;
  }

  public List<String> getFolders() {
    return folders;
  }

  public void setFolders(List<String> folders) {
    this.folders = folders;
  }
}
