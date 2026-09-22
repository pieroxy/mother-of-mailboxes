package net.pieroxy.mom.rules.actions;

import javax.mail.Message;

/**
 * Test double: there's currently no real Action implementation that succeeds (MoveToAction is a
 * stub that always returns false), so this is needed to test AndAction/OrAction short-circuiting.
 */
class StubAction extends Action {
  private final boolean result;
  int callCount = 0;

  StubAction(boolean result) {
    this.result = result;
  }

  @Override
  public boolean run(Message message) {
    callCount++;
    return result;
  }
}
