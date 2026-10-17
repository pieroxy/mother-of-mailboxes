package net.pieroxy.mom.rules.actions;

import net.pieroxy.mom.rules.actions.implementations.OrAction;
import org.junit.Test;

import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.util.Arrays;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OrActionTest {
  private final MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));

  @Test
  public void stopsAtFirstSuccess() throws Exception {
    StubAction a = new StubAction(true);
    StubAction b = new StubAction(true);
    OrAction or = new OrAction();
    or.setChildren(Arrays.asList(a, b));

    assertTrue(or.run(message));
    assertEquals(1, a.callCount);
    assertEquals("b must never run since a already succeeded", 0, b.callCount);
  }

  @Test
  public void triesNextChildOnFailure() throws Exception {
    StubAction a = new StubAction(false);
    StubAction b = new StubAction(true);
    OrAction or = new OrAction();
    or.setChildren(Arrays.asList(a, b));

    assertTrue(or.run(message));
    assertEquals(1, a.callCount);
    assertEquals(1, b.callCount);
  }

  @Test
  public void failsWhenAllChildrenFail() throws Exception {
    StubAction a = new StubAction(false);
    StubAction b = new StubAction(false);
    OrAction or = new OrAction();
    or.setChildren(Arrays.asList(a, b));

    assertFalse(or.run(message));
  }

  @Test
  public void emptyChildrenFails() throws Exception {
    assertFalse(new OrAction().run(message));
  }
}
