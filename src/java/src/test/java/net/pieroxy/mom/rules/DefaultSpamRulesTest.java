package net.pieroxy.mom.rules;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.pieroxy.mom.config.general.MailFilterRuleConfiguration;
import org.junit.Test;

import java.io.FileReader;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DefaultSpamRulesTest {
  private static final Set<String> ALL_LISTS = Set.of("spamhaus-drop", "blocklist-de-mail", "hagezi-tif-mini",
      "blocklist-project-phishing", "disposable-email-domains", "hagezi-nrd7");

  @Test
  public void matchesConfigExampleWhenEveryListIsConfigured() throws Exception {
    JsonObject example = JsonParser.parseReader(new FileReader("../../config.example.json")).getAsJsonObject();
    JsonElement exampleRules = example.getAsJsonArray("configurations").get(0).getAsJsonObject().get("rules");
    // Round-tripped through the config classes so defaults (keepProcessing...) compare equal.
    Gson gson = new Gson();
    JsonElement expected = gson.toJsonTree(gson.fromJson(exampleRules, MailFilterRuleConfiguration[].class));

    JsonElement actual = gson.toJsonTree(DefaultSpamRules.build("Spam", "SpamML", ALL_LISTS).toArray(new MailFilterRuleConfiguration[0]));

    assertEquals(expected, actual);
  }

  @Test
  public void usesTheGivenFolders() {
    String json = new Gson().toJson(DefaultSpamRules.build("Junk", "Junk/ML", ALL_LISTS));

    assertFalse(json.contains("\"Spam"));
    assertTrue(json.contains("\"Junk/ML\""));
  }

  @Test
  public void withoutReputationListsOnlyTheProtocolAndClassifierRulesRemain() {
    String json = new Gson().toJson(DefaultSpamRules.build("Spam", "SpamML", Set.of()));

    assertFalse(json.contains("REPUTATION"));
    assertEquals(6, DefaultSpamRules.build("Spam", "SpamML", Set.of()).size());
  }

  @Test
  public void aPairWithOneListMissingKeepsOnlyASingleUnreadRuleForTheOther() {
    List<MailFilterRuleConfiguration> rules = DefaultSpamRules.build("Spam", "SpamML", Set.of("spamhaus-drop"));

    assertEquals(7, rules.size());
    MailFilterRuleConfiguration last = rules.get(6);
    assertEquals(Set.of("spamhaus-drop"), last.getMatcher().getListIds());
    assertEquals("MOVE_TO", last.getAction().getType().name());
  }
}
