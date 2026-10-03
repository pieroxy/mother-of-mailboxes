package net.pieroxy.mom.learning;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.pieroxy.mom.config.general.MailFilterRuleConfiguration;
import net.pieroxy.mom.rules.RuleContext;
import net.pieroxy.mom.utils.mail.ImapMailboxConnection;
import org.junit.Test;

import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A message that can't actually be learned from (e.g. no From header at all — FromAddressMatcher
 * and friends all reject that, see extractKeyFromExample) must not be left sitting in its
 * mom-rules/ folder forever: without this, it would be retried, and rewarned about, on every
 * single cycle with no way out short of moving it by hand.
 */
public class RuleLearnerUnlearnableExampleTest extends AbstractRuleLearnerTest {
  @Test
  public void movesAnUnlearnableExampleToDoneInsteadOfLeavingItStuck() throws Exception {
    MimeMessage messageWithNoFromHeader = new MimeMessage(Session.getDefaultInstance(new Properties()));
    messageWithNoFromHeader.setSubject("Test");
    messageWithNoFromHeader.setText("Hello");
    fixture.appendMessage(messageWithNoFromHeader, "mom-rules", "FROM_ADDRESS_EQUALS", "MOVE_TO", "Spam");

    File statsDir = new File(tempFolder.getRoot(), "logs");
    RuleContext context = new RuleContext(null, null, null, statsDir, 10);
    boolean learnedSomething;
    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      RuleLearner learner = new RuleLearner(mailbox, store(), List.of(), false, context);
      learner.ensureFolderSkeleton();
      learnedSomething = learner.learnFromExamples();
    }

    assertFalse("extraction failed: nothing should have been learned", learnedSomething);

    List<MailFilterRuleConfiguration> learned = store().load();
    assertEquals(0, learned.size());

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      assertEquals("the unlearnable example must not be retried forever", 0,
          mailbox.getAllMessages(mailbox.getOrCreateFolder("mom-rules", "FROM_ADDRESS_EQUALS", "MOVE_TO", "Spam")).length);
      assertEquals("it must be filed away in Done instead", 1,
          mailbox.getAllMessages(mailbox.getOrCreateFolder("mom-rules", "Done")).length);
    }

    String today = LocalDate.now(ZoneOffset.UTC).toString();
    List<String> lines = Files.readAllLines(new File(statsDir, "decisions-" + today + ".json").toPath());
    assertEquals(1, lines.size());
    JsonObject entry = new Gson().fromJson(lines.get(0), JsonObject.class);
    assertEquals("LEARNING", entry.get("trigger").getAsString());
    assertFalse("nothing was learned: no learnedRule field", entry.has("learnedRule"));
    assertTrue(entry.get("reasonForMoveToDone").getAsString().startsWith("could not learn a rule:"));
  }
}
