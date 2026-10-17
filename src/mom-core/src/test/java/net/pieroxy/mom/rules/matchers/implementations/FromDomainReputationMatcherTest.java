package net.pieroxy.mom.rules.matchers.implementations;

import net.jpountz.lz4.LZ4FrameOutputStream;
import net.pieroxy.mom.config.general.MailFilterRuleMatcherConfiguration;
import net.pieroxy.mom.config.general.ReputationListConfig;
import net.pieroxy.mom.detection.reputation.ReputationListType;
import net.pieroxy.mom.detection.reputation.ReputationRegistry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FromDomainReputationMatcherTest {
  private final Session session = Session.getDefaultInstance(new Properties());

  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  private ReputationRegistry registryWithDomainList(String id, double score, String... domains) throws Exception {
    String dataFolder = tempFolder.newFolder(id).getAbsolutePath();
    writeCache(dataFolder, id, String.join("\n", domains) + "\n");
    ReputationListConfig cfg = new ReputationListConfig();
    cfg.setId(id);
    cfg.setType(ReputationListType.DOMAIN);
    cfg.setUrl("file:///unused");
    cfg.setRefreshHours(24);
    cfg.setScore(score);
    return new ReputationRegistry(List.of(cfg), dataFolder);
  }

  private void writeCache(String dataFolder, String id, String content) throws Exception {
    File folder = new File(dataFolder, "reputation");
    folder.mkdirs();
    File file = new File(folder, id + ".txt.lz4");
    try (Writer w = new OutputStreamWriter(new LZ4FrameOutputStream(new FileOutputStream(file)), StandardCharsets.UTF_8)) {
      w.write(content);
    }
  }

  private FromDomainReputationMatcher matcherFor(String key, Set<String> listIds, ReputationRegistry registry) {
    MailFilterRuleMatcherConfiguration config = new MailFilterRuleMatcherConfiguration();
    config.setKey(key);
    config.setListIds(listIds);
    FromDomainReputationMatcher matcher = new FromDomainReputationMatcher(registry);
    matcher.setConfig(config);
    return matcher;
  }

  @Test
  public void matchesWhenScoreCrossesTheThreshold() throws Exception {
    ReputationRegistry registry = registryWithDomainList("blocklist", 0.9, "spammy.example.com");
    FromDomainReputationMatcher matcher = matcherFor(">0.5", Set.of("blocklist"), registry);

    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("sender@spammy.example.com"));

    assertTrue(matcher.matches(message).matched());
  }

  @Test
  public void debugStringNamesTheReputationListThatMatched() throws Exception {
    ReputationRegistry registry = registryWithDomainList("blocklist", 0.9, "spammy.example.com");
    FromDomainReputationMatcher matcher = matcherFor(">0.5", Set.of("blocklist"), registry);

    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("sender@spammy.example.com"));

    assertEquals("FromDomainReputationMatcher[blocklist](score=0.9)", matcher.matches(message).debugString());
  }

  @Test
  public void matchesCaseInsensitively() throws Exception {
    ReputationRegistry registry = registryWithDomainList("blocklist", 0.9, "spammy.example.com");
    FromDomainReputationMatcher matcher = matcherFor(">0.5", Set.of("blocklist"), registry);

    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("sender@Spammy.Example.COM"));

    assertTrue(matcher.matches(message).matched());
  }

  @Test
  public void doesNotMatchBelowThreshold() throws Exception {
    ReputationRegistry registry = registryWithDomainList("watchlist", 0.2, "spammy.example.com");
    FromDomainReputationMatcher matcher = matcherFor(">0.5", Set.of("watchlist"), registry);

    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("sender@spammy.example.com"));

    assertFalse(matcher.matches(message).matched());
  }

  @Test
  public void doesNotMatchWhenDomainIsNotInAnyReferencedList() throws Exception {
    ReputationRegistry registry = registryWithDomainList("blocklist", 1.0, "spammy.example.com");
    FromDomainReputationMatcher matcher = matcherFor(">0.5", Set.of("blocklist"), registry);

    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("sender@legit.example.com"));

    assertFalse(matcher.matches(message).matched());
  }

  @Test
  public void doesNotMatchWhenMultipleFromAddresses() throws Exception {
    ReputationRegistry registry = registryWithDomainList("blocklist", 1.0, "spammy.example.com");
    FromDomainReputationMatcher matcher = matcherFor(">0.5", Set.of("blocklist"), registry);

    MimeMessage message = new MimeMessage(session);
    message.addFrom(new InternetAddress[]{
            new InternetAddress("a@spammy.example.com"),
            new InternetAddress("b@spammy.example.com")
    });

    assertFalse(matcher.matches(message).matched());
  }

  @Test
  public void missingListIdsIsRejectedAtConfigTime() {
    MailFilterRuleMatcherConfiguration config = new MailFilterRuleMatcherConfiguration();
    config.setKey(">0.5");
    FromDomainReputationMatcher matcher = new FromDomainReputationMatcher(ReputationRegistry.empty());

    try {
      matcher.setConfig(config);
      fail("should have thrown");
    } catch (IllegalArgumentException expected) {
      // ok
    }
  }
}
