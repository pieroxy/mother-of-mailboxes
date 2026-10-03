package net.pieroxy.mom.learning;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.pieroxy.mom.config.general.MailFilterRuleConfiguration;
import net.pieroxy.mom.rules.RuleContext;
import net.pieroxy.mom.utils.mail.ImapMailboxConnection;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RuleLearnerLearnsFromExampleTest extends AbstractRuleLearnerTest {
  @Test
  public void learnsARuleFromAnExampleAndRunsItsAction() throws Exception {
    fixture.appendMessage(messageFrom("sender@newsletter.example.com"), "mom-rules", "FROM_DOMAIN_EQUALS", "MOVE_TO", "Spam");

    File statsDir = new File(tempFolder.getRoot(), "logs");
    RuleContext context = new RuleContext(null, null, null, statsDir, 10);
    boolean learnedSomething;
    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      RuleLearner learner = new RuleLearner(mailbox, store(), List.of(), false, context);
      learner.ensureFolderSkeleton();
      learnedSomething = learner.learnFromExamples();
    }

    assertTrue(learnedSomething);

    List<MailFilterRuleConfiguration> learned = store().load();
    assertEquals(1, learned.size());
    assertEquals("newsletter.example.com", learned.get(0).getMatcher().getKey());

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      assertEquals(1, mailbox.getAllMessages(mailbox.getOrCreateFolder("Spam")).length);
      assertEquals(0, mailbox.getAllMessages(mailbox.getOrCreateFolder("mom-rules", "FROM_DOMAIN_EQUALS", "MOVE_TO", "Spam")).length);
    }

    String today = LocalDate.now(ZoneOffset.UTC).toString();
    List<String> lines = Files.readAllLines(new File(statsDir, "decisions-" + today + ".json").toPath());
    assertEquals(1, lines.size());
    JsonObject entry = new Gson().fromJson(lines.get(0), JsonObject.class);
    assertEquals("LEARNING", entry.get("trigger").getAsString());
    assertTrue(entry.get("learnedRule").getAsString().contains("FROM_DOMAIN_EQUALS(newsletter.example.com)"));
    assertFalse("MOVE_TO already relocated the example on its own: no need to also move it to Done",
        entry.has("reasonForMoveToDone"));
  }
}
