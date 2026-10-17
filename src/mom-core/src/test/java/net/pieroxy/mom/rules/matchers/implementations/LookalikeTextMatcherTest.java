package net.pieroxy.mom.rules.matchers.implementations;

import net.pieroxy.mom.config.general.MailFilterRuleMatcherConfiguration;
import net.pieroxy.mom.rules.matchers.MatchResult;
import org.junit.Test;

import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LookalikeTextMatcherTest {
  private static MimeMessage message(String subject) throws Exception {
    String raw = "From: a@example.com\r\nSubject: " + subject + "\r\n\r\nbody\r\n";
    return new MimeMessage(Session.getDefaultInstance(new Properties()), new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
  }

  private static LookalikeTextMatcher matcher(String... keys) {
    MailFilterRuleMatcherConfiguration config = new MailFilterRuleMatcherConfiguration();
    config.setKeys(Set.of(keys));
    LookalikeTextMatcher matcher = new LookalikeTextMatcher();
    matcher.setConfig(config);
    return matcher;
  }

  @Test
  public void matchesTheConfiguredKindsCaseInsensitivelyAndNamesTheWord() throws Exception {
    MatchResult result = matcher("MIXED_SCRIPT", "whole_script").matches(message("Your Pаypal account"));

    assertTrue(result.matched());
    assertEquals("LookalikeTextMatcher(subject: Pаypal (Latin+Cyrillic))", result.debugString());
  }

  @Test
  public void ignoresKindsThatAreNotConfigured() throws Exception {
    assertFalse(matcher("fancy_letters").matches(message("Your Pаypal account")).matched());
    assertFalse(matcher("mixed_script", "whole_script", "fancy_letters").matches(message("Your PayPal account")).matched());
  }
}
