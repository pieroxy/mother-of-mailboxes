package net.pieroxy.mom.rules.matchers.implementations;

import net.pieroxy.mom.config.general.MailFilterRuleMatcherConfiguration;
import net.pieroxy.mom.rules.matchers.Matcher;
import net.pieroxy.mom.rules.matchers.MatcherType;
import org.junit.Test;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ListIdMatcherTest {
  private final Session session = Session.getInstance(new Properties());

  private static Matcher matcher(String key) {
    MailFilterRuleMatcherConfiguration config = new MailFilterRuleMatcherConfiguration();
    config.setType(MatcherType.LIST_ID_EQUALS);
    config.setKey(key);
    return Matcher.build(config);
  }

  private MimeMessage message(String headers) throws Exception {
    String raw = "From: alice@example.org\r\nSubject: Hello\r\n" + headers + "\r\nBody\r\n";
    return new MimeMessage(session, new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  public void matchesTheBracketedIdIgnoringCaseAndDisplayName() throws Exception {
    Matcher matcher = matcher("Dev.Lists.example.org");

    assertTrue(matcher.matches(message("List-Id: \"Dev discussions\" <dev.lists.example.org>\r\n")).matched());
    assertTrue(matcher.matches(message("List-Id: Renamed <DEV.lists.example.org>\r\n")).matched());
    assertTrue(matcher.matches(message("List-Id: <dev.lists.example.org>\r\n")).matched());
    assertFalse(matcher.matches(message("List-Id: <users.lists.example.org>\r\n")).matched());
  }

  @Test
  public void matchesAFoldedHeader() throws Exception {
    assertTrue(matcher("dev.lists.example.org")
        .matches(message("List-Id: \"A rather long display name\"\r\n <dev.lists.example.org>\r\n")).matched());
  }

  @Test
  public void fallsBackToTheWholeValueWithoutBrackets() throws Exception {
    assertTrue(matcher("dev.lists.example.org").matches(message("List-Id:  dev.lists.example.org \r\n")).matched());
  }

  @Test
  public void readsTheTopmostOccurrenceOnly() throws Exception {
    Matcher matcher = matcher("dev.lists.example.org");

    assertTrue(matcher.matches(message("List-Id: <dev.lists.example.org>\r\nList-Id: <other.example.org>\r\n")).matched());
    assertFalse(matcher.matches(message("List-Id: <other.example.org>\r\nList-Id: <dev.lists.example.org>\r\n")).matched());
  }

  @Test
  public void doesNotMatchWithoutListId() throws Exception {
    assertFalse(matcher("dev.lists.example.org").matches(message("")).matched());
  }

  @Test
  public void matchesAnyOfSeveralKeys() throws Exception {
    MailFilterRuleMatcherConfiguration config = new MailFilterRuleMatcherConfiguration();
    config.setType(MatcherType.LIST_ID_EQUALS);
    config.setKeys(Set.of("dev.lists.example.org", "users.lists.example.org"));
    Matcher matcher = Matcher.build(config);

    assertTrue(matcher.matches(message("List-Id: <users.lists.example.org>\r\n")).matched());
    assertFalse(matcher.matches(message("List-Id: <announce.lists.example.org>\r\n")).matched());
  }

  @Test
  public void learnsTheLowerCasedId() throws Exception {
    assertEquals("dev.lists.example.org",
        new ListIdMatcher().extractKeyFromExample(message("List-Id: \"Dev\" <Dev.Lists.Example.org>\r\n")));
  }

  @Test
  public void refusesToLearnWithoutListId() throws Exception {
    for (String headers : new String[] {"", "List-Id: \"Dev\" <>\r\n"}) {
      try {
        new ListIdMatcher().extractKeyFromExample(message(headers));
        fail("expected a MessagingException for: " + headers);
      } catch (MessagingException expected) {
        assertTrue(expected.getMessage().contains("LIST_ID_EQUALS"));
      }
    }
  }

  @Test
  public void parsesHeaderValues() {
    assertEquals("dev.example.org", ListIdMatcher.listIdOf("Dev <dev.example.org>"));
    assertEquals("dev.example.org", ListIdMatcher.listIdOf("dev.example.org"));
    assertNull(ListIdMatcher.listIdOf("   "));
  }
}
