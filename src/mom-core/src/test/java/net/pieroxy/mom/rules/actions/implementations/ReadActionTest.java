package net.pieroxy.mom.rules.actions.implementations;

import org.junit.Test;

import javax.mail.Flags;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReadActionTest {
  private final MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));

  @Test
  public void marksTheMessageAsSeen() throws Exception {
    assertFalse(message.isSet(Flags.Flag.SEEN));

    assertTrue(new ReadAction().run(message));

    assertTrue(message.isSet(Flags.Flag.SEEN));
  }
}
