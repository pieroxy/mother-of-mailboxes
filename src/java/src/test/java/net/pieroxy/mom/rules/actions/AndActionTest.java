package net.pieroxy.mom.rules.actions;

import net.pieroxy.mom.rules.actions.implementations.AndAction;
import org.junit.Test;

import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.util.Arrays;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AndActionTest {
  private final MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));

  @Test
  public void runsAllChildrenAndSucceedsWhenAllSucceed() throws Exception {
    StubAction a = new StubAction(true);
    StubAction b = new StubAction(true);
    AndAction and = new AndAction();
    and.setChildren(Arrays.asList(a, b));

    assertTrue(and.run(message));
    assertEquals(1, a.callCount);
    assertEquals(1, b.callCount);
  }

  @Test
  public void stopsAtFirstFailure() throws Exception {
    StubAction a = new StubAction(false);
    StubAction b = new StubAction(true);
    AndAction and = new AndAction();
    and.setChildren(Arrays.asList(a, b));

    assertFalse(and.run(message));
    assertEquals(1, a.callCount);
    assertEquals("b must never run after a fails", 0, b.callCount);
  }

  @Test
  public void emptyChildrenSucceedsVacuously() throws Exception {
    assertTrue(new AndAction().run(message));
  }
}
