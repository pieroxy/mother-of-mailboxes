package net.pieroxy.mom.rules.actions.implementations;

import net.pieroxy.mom.rules.actions.Action;

import javax.mail.Message;
import javax.mail.MessagingException;

/**
 * Runs the child actions in order, stopping as soon as one succeeds (fallback chain). With no
 * children, an OR fails by convention.
 */
public class OrAction extends Action {
  @Override
  public boolean run(Message message) throws MessagingException {
    for (Action child : getChildren()) {
      boolean result = child.run(message);
      getLogger().fine(() -> "OR: child " + child.getClass().getSimpleName() + " -> " + result);
      if (result) return true;
    }
    return false;
  }
}
