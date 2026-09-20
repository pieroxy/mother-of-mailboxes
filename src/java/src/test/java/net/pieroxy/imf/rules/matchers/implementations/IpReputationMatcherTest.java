package net.pieroxy.imf.rules.matchers.implementations;

import net.jpountz.lz4.LZ4FrameOutputStream;
import net.pieroxy.imf.config.general.MailFilterRuleMatcherConfiguration;
import net.pieroxy.imf.config.general.ReputationListConfig;
import net.pieroxy.imf.detection.reputation.ReputationListType;
import net.pieroxy.imf.detection.reputation.ReputationRegistry;
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

public class IpReputationMatcherTest {
  private final Session session = Session.getDefaultInstance(new Properties());

  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  private ReputationRegistry registryWithIpList(String id, double score, String... cidrLines) throws Exception {
    String dataFolder = tempFolder.newFolder(id).getAbsolutePath();
    String content = String.join("\n", cidrLines) + "\n";
    writeCache(dataFolder, id, content);
    ReputationListConfig cfg = new ReputationListConfig();
    cfg.setId(id);
    cfg.setType(ReputationListType.IP_CIDR);
    cfg.setUrl("file:///unused");
    cfg.setRefreshHours(24);
    cfg.setScore(score);
    return new ReputationRegistry(List.of(cfg), dataFolder);
  }

  // Writes directly into the disk cache in the format ReputationRegistry expects
  // (dataFolder/reputation/<id>.txt.lz4): ReputationListStore is package-private to the
  // reputation package, inaccessible from here, so we just reproduce the lz4 write itself.
  private void writeCache(String dataFolder, String id, String content) throws Exception {
    File folder = new File(dataFolder, "reputation");
    folder.mkdirs();
    File file = new File(folder, id + ".txt.lz4");
    try (Writer w = new OutputStreamWriter(new LZ4FrameOutputStream(new FileOutputStream(file)), StandardCharsets.UTF_8)) {
      w.write(content);
    }
  }

  private IpReputationMatcher matcherFor(String key, Set<String> listIds, ReputationRegistry registry) {
    MailFilterRuleMatcherConfiguration config = new MailFilterRuleMatcherConfiguration();
    config.setKey(key);
    config.setListIds(listIds);
    IpReputationMatcher matcher = new IpReputationMatcher(registry);
    matcher.setConfig(config);
    return matcher;
  }

  private MimeMessage messageFromIp(String ip) throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.addHeader("Received", "from mail.example.com (mail.example.com [" + ip + "])\n"
            + "\tby mx.myprovider.com with SMTPS id abc; Mon, 31 Aug 2026 10:00:00 +0000");
    message.setFrom(new InternetAddress("sender@example.com"));
    return message;
  }

  @Test
  public void matchesWhenScoreCrossesTheThreshold() throws Exception {
    ReputationRegistry registry = registryWithIpList("blocklist", 0.9, "203.0.113.0/24");
    IpReputationMatcher matcher = matcherFor(">0.5", Set.of("blocklist"), registry);

    assertTrue(matcher.matches(messageFromIp("203.0.113.10")).matched());
  }

  @Test
  public void debugStringNamesTheReputationListThatMatched() throws Exception {
    ReputationRegistry registry = registryWithIpList("blocklist", 0.9, "203.0.113.0/24");
    IpReputationMatcher matcher = matcherFor(">0.5", Set.of("blocklist"), registry);

    assertEquals("IpReputationMatcher[blocklist](score=0.9)",
        matcher.matches(messageFromIp("203.0.113.10")).debugString());
  }

  @Test
  public void doesNotMatchBelowThreshold() throws Exception {
    ReputationRegistry registry = registryWithIpList("watchlist", 0.2, "203.0.113.0/24");
    IpReputationMatcher matcher = matcherFor(">0.5", Set.of("watchlist"), registry);

    assertFalse(matcher.matches(messageFromIp("203.0.113.10")).matched());
  }

  @Test
  public void doesNotMatchWhenIpIsNotInAnyReferencedList() throws Exception {
    ReputationRegistry registry = registryWithIpList("blocklist", 1.0, "203.0.113.0/24");
    IpReputationMatcher matcher = matcherFor(">0.5", Set.of("blocklist"), registry);

    assertFalse(matcher.matches(messageFromIp("198.51.100.1")).matched());
  }

  @Test
  public void doesNotMatchWhenNoConnectingIpCanBeDetermined() throws Exception {
    ReputationRegistry registry = registryWithIpList("blocklist", 1.0, "203.0.113.0/24");
    IpReputationMatcher matcher = matcherFor(">0.5", Set.of("blocklist"), registry);

    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("sender@example.com"));

    assertFalse(matcher.matches(message).matched());
  }

  @Test
  public void missingListIdsIsRejectedAtConfigTime() {
    MailFilterRuleMatcherConfiguration config = new MailFilterRuleMatcherConfiguration();
    config.setKey(">0.5");
    IpReputationMatcher matcher = new IpReputationMatcher(ReputationRegistry.empty());

    try {
      matcher.setConfig(config);
      fail("should have thrown");
    } catch (IllegalArgumentException expected) {
      // ok
    }
  }

  @Test
  public void malformedThresholdIsRejectedAtConfigTime() {
    MailFilterRuleMatcherConfiguration config = new MailFilterRuleMatcherConfiguration();
    config.setKey("spammy");
    config.setListIds(Set.of("blocklist"));
    IpReputationMatcher matcher = new IpReputationMatcher(ReputationRegistry.empty());

    try {
      matcher.setConfig(config);
      fail("should have thrown");
    } catch (IllegalArgumentException expected) {
      // ok
    }
  }
}
