package net.pieroxy.mom.rules.actions.implementations;

import org.junit.Test;

import javax.mail.Flags;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NoopActionTest {
  private final MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));

  @Test
  public void succeedsWithoutTouchingTheMessage() throws Exception {
    assertTrue(new NoopAction().run(message));

    assertFalse("must not have flagged the message as read", message.isSet(Flags.Flag.SEEN));
  }
}
