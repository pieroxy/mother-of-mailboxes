package net.pieroxy.mom.rules.actions;

import net.pieroxy.mom.config.general.MailFilterRuleActionConfiguration;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class ActionNamesTest {
  private static MailFilterRuleActionConfiguration action(ActionType type, String key) {
    MailFilterRuleActionConfiguration action = new MailFilterRuleActionConfiguration();
    action.setType(type);
    action.setKey(key);
    return action;
  }

  @Test
  public void generatesANameFromTypeAndKey() {
    assertEquals("→ Backups", ActionNames.nameOf(action(ActionType.MOVE_TO, "Backups")));
    assertEquals("→ Spam (read)", ActionNames.nameOf(action(ActionType.MOVE_TO_AND_READ, "Spam")));
    assertEquals("Mark read", ActionNames.nameOf(action(ActionType.READ, null)));
    assertEquals("No action", ActionNames.nameOf(action(ActionType.NOOP, "log this")));
    assertEquals("Forward to a@b.c", ActionNames.nameOf(action(ActionType.FORWARD, "a@b.c")));
  }

  @Test
  public void namesACompositeAfterItsChildren() {
    MailFilterRuleActionConfiguration and = action(ActionType.AND, null);
    and.setChildren(List.of(action(ActionType.READ, null), action(ActionType.MOVE_TO, "Archive")));
    assertEquals("Mark read + → Archive", ActionNames.nameOf(and));
  }

  @Test
  public void aConfiguredNameWinsUnlessBlank() {
    MailFilterRuleActionConfiguration named = action(ActionType.MOVE_TO, "Admin/Backups");
    named.setName("  Backups ");
    assertEquals("Backups", ActionNames.nameOf(named));
    named.setName(" ");
    assertEquals("→ Admin/Backups", ActionNames.nameOf(named));
  }
}
