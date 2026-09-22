package net.pieroxy.mom.rules.actions.implementations;

import net.pieroxy.mom.rules.actions.Action;

import javax.mail.Message;

/**
 * Does nothing to the message, always succeeds. Useful to log/observe a match (via the action's
 * own {@code logLevel}, or as one branch of an {@code AND}/{@code OR}) without actually acting on
 * the mail — e.g. a rule built only to compare against another matcher's verdict, combined with
 * {@code "keepProcessing": true} so the real rules further down still get evaluated.
 */
public class NoopAction extends Action {
  @Override
  public boolean run(Message message) {
    getLogger().fine(() -> "No-op");
    return true;
  }
}
