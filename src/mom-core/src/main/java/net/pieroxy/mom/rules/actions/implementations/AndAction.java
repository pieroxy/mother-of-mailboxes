package net.pieroxy.mom.rules.actions.implementations;

import net.pieroxy.mom.rules.actions.Action;

import javax.mail.Message;
import javax.mail.MessagingException;

/**
 * Runs the child actions in order, stopping at the first one that fails. With no children, an
 * AND succeeds by convention (vacuous truth).
 */
public class AndAction extends Action {
  @Override
  public boolean run(Message message) throws MessagingException {
    for (Action child : getChildren()) {
      boolean result = child.run(message);
      getLogger().fine(() -> "AND: child " + child.getClass().getSimpleName() + " -> " + result);
      if (!result) return false;
    }
    return true;
  }
}
