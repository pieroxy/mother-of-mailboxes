package net.pieroxy.mom.rules.actions;

import net.pieroxy.mom.config.general.MailFilterRuleActionConfiguration;
import net.pieroxy.mom.rules.actions.implementations.AndAction;
import net.pieroxy.mom.rules.actions.implementations.MoveToAction;
import net.pieroxy.mom.rules.actions.implementations.ReadAction;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ActionTypeTest {

  @Test
  public void onlyLeafTypesAreLearnable() {
    assertTrue(ActionType.learnableValues().contains(ActionType.MOVE_TO));
    assertFalse(ActionType.learnableValues().contains(ActionType.READ));
    assertTrue(ActionType.learnableValues().contains(ActionType.MOVE_TO_AND_READ));
    assertFalse(ActionType.learnableValues().contains(ActionType.AND));
    assertFalse(ActionType.learnableValues().contains(ActionType.OR));
    assertFalse(ActionType.learnableValues().contains(ActionType.NOOP));
  }

  @Test
  public void moveToAndReadRunsReadBeforeMoveTo() {
    // Order matters: MoveToAction copies the message with its current flags, so READ must run
    // first, so that \Seen is already set by the time the copy to the target happens.
    MailFilterRuleActionConfiguration config = new MailFilterRuleActionConfiguration();
    config.setType(ActionType.MOVE_TO_AND_READ);
    config.setKey("Target");

    Action action = Action.build(config);

    assertTrue(action instanceof AndAction);
    List<Action> children = action.getChildren();
    assertEquals(2, children.size());
    assertTrue("READ must run first", children.get(0) instanceof ReadAction);
    assertTrue("MOVE_TO must run second", children.get(1) instanceof MoveToAction);
    assertEquals("Target", children.get(1).getConfig().getKey());
  }
}
