package net.pieroxy.mom.detection.reputation;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.pieroxy.mom.config.general.ReputationListConfig;
import org.junit.Test;

import java.io.FileReader;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class RecommendedReputationListsTest {
  @Test
  public void matchesConfigExample() throws Exception {
    JsonObject example = JsonParser.parseReader(new FileReader("../../config.example.json")).getAsJsonObject();
    Gson gson = new Gson();
    JsonElement expected = gson.toJsonTree(gson.fromJson(example.get("reputationLists"), ReputationListConfig[].class));

    JsonElement actual = gson.toJsonTree(RecommendedReputationLists.ALL.stream()
        .map(RecommendedReputationLists.Recommended::config).collect(Collectors.toList()).toArray(new ReputationListConfig[0]));

    assertEquals(expected, actual);
  }
}
