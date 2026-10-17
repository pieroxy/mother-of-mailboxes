package net.pieroxy.mom.rules.actions.implementations;

import net.pieroxy.mom.rules.actions.Action;
import net.pieroxy.mom.utils.mail.ImapMailboxConnection;

import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Store;

/**
 * Copies the message into the target folder (created if it doesn't exist, along with any
 * missing intermediate folder for a nested {@code key} like {@code "Admin/Backups"}) then marks
 * it \Deleted in the source folder; the actual deletion happens when the caller closes
 * (expunges) the source folder.
 */
public class MoveToAction extends Action {
  @Override
  public boolean run(Message message) throws MessagingException {
    Folder source = message.getFolder();
    Folder target = resolveTarget(source.getStore(), getConfig().getKey());
    getLogger().fine(() -> "Moving message from " + source.getFullName() + " to " + getConfig().getKey());
    source.copyMessages(new Message[]{message}, target);
    message.setFlag(Flags.Flag.DELETED, true);
    return true;
  }

  /**
   * {@code key} is always "/"-separated in config, regardless of the server's actual hierarchy
   * delimiter (which can be anything — {@code .}, {@code ^}...): resolving it level by level via
   * {@code getFolder(segment)} on each already-resolved parent, like
   * {@link ImapMailboxConnection#getOrCreateFolder}, is what makes that
   * safe. Handing the whole string to {@code store.getFolder(fullName)} instead — as this used
   * to do — has the server interpret it using its own real delimiter, so a literal "/" in it
   * either ends up part of one folder's name or gets mangled into that other character; either
   * way, not the nested folder the config asked for.
   */
  private Folder resolveTarget(Store store, String key) throws MessagingException {
    Folder current = store.getDefaultFolder();
    for (String segment : key.split("/")) {
      if (segment.isEmpty()) continue; // tolerate a stray leading/trailing/doubled "/" in config
      current = current.getFolder(segment);
      if (!current.exists()) {
        current.create(Folder.HOLDS_MESSAGES | Folder.HOLDS_FOLDERS);
        getLogger().fine(() -> "Created missing target folder segment " + segment);
      }
    }
    return current;
  }
}
