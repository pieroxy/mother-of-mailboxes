package net.pieroxy.mom.rules;

import net.pieroxy.mom.config.general.MailAccountConfiguration;
import net.pieroxy.mom.detection.classifier.ClassifierCorpusStore;
import net.pieroxy.mom.detection.classifier.ClassifierLabel;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/** The corpus scan of every folder but Spam runs once a day, and on a cycle requested by hand. */
public class MailAccountCorpusScanTest extends AbstractMailAccountTest {

  @Test
  public void mailFiledAfterTodaysScanIsOnlyLearnedFromOnACycleRequestedByHand() throws Exception {
    MailAccountConfiguration config = fixture.accountConfig("test-account");
    config.setRules(List.of());
    config.setClassifierCorpusRetentionDays(30);
    config.setClassifierSpamFolderName("Spam");
    MailAccount account = new MailAccount(config, fixture.accountCredential(), tempFolder.getRoot().getAbsolutePath(),
        (c, credential) -> fixture.connectAsImapMailbox());
    ClassifierCorpusStore corpus = new ClassifierCorpusStore(tempFolder.getRoot().getAbsolutePath(), "test-account", 30);

    account.processMessages();
    fixture.appendMessage(messageFrom("friend@example.com"), "Archive");
    account.processMessages();
    assertEquals("today's scan already ran", 0, corpus.readAll().size());

    account.requestCycle();
    account.processMessages();
    assertEquals(1, corpus.readAll().size());
    assertEquals(ClassifierLabel.HAM, corpus.readAll().get(0).getLabel());
  }
}
