package net.pieroxy.mom.rules.actions.implementations;

import net.pieroxy.mom.rules.actions.Action;

import javax.mail.Flags;
import javax.mail.Message;
import javax.mail.MessagingException;

/** Marks the message as read (\Seen flag). */
public class ReadAction extends Action {
  @Override
  public boolean run(Message message) throws MessagingException {
    getLogger().fine(() -> "Marking message as read");
    message.setFlag(Flags.Flag.SEEN, true);
    return true;
  }
}
