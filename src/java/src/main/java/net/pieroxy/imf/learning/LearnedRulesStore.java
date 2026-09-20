package net.pieroxy.imf.learning;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import net.pieroxy.imf.config.general.MailFilterRuleConfiguration;
import net.pieroxy.imf.config.general.MailFilterRuleMatcherConfiguration;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Persists, per account, the rules automatically learned by moving example messages into the
 * imf-rules/ tree. A file separate from the manual config (config.json), so as to never
 * overwrite what the user edits by hand.
 */
public class LearnedRulesStore {
  private final static Logger LOGGER = Logger.getLogger(LearnedRulesStore.class.getName());
  // Hand-edited when corrections are needed (no UI for now): pretty-printed to stay
  // readable/editable without having to reformat it yourself first.
  private final static Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private final static Type LIST_TYPE = new TypeToken<List<MailFilterRuleConfiguration>>() {}.getType();

  private final File file;

  public LearnedRulesStore(String dataFolder, String accountKey) {
    this.file = new File(dataFolder, accountKey + "-learned-rules.json");
  }

  public List<MailFilterRuleConfiguration> load() {
    if (!file.exists()) return new ArrayList<>();
    try (FileReader r = new FileReader(file)) {
      List<MailFilterRuleConfiguration> rules = GSON.fromJson(r, LIST_TYPE);
      return rules != null ? rules : new ArrayList<>();
    } catch (IOException | JsonParseException e) {
      LOGGER.log(Level.WARNING, "Could not read learned rules file " + file, e);
      return new ArrayList<>();
    }
  }

  public void save(List<MailFilterRuleConfiguration> rules) {
    file.getParentFile().mkdirs();
    try (FileWriter w = new FileWriter(file)) {
      GSON.toJson(rules, LIST_TYPE, w);
    } catch (IOException e) {
      LOGGER.log(Level.WARNING, "Could not write learned rules file " + file, e);
    }
  }

  /**
   * Adds the rule if its matcher key isn't already covered by an equivalent rule (same matcher
   * type, same action type+key). If such a rule already exists but with a different matcher
   * key, the new key is merged into the existing rule (matcher.keys) instead of adding a whole
   * duplicate rule just for one more key — many learned rules share the same action (e.g.
   * several senders all sent to Spam). Also compacts, in passing, any duplicates already
   * present in the file (e.g. learned before this merging existed).
   * @return true if the rule was actually added, or a key actually merged.
   */
  public boolean addIfAbsent(MailFilterRuleConfiguration rule) {
    List<MailFilterRuleConfiguration> rules = load();
    boolean compacted = compact(rules);
    String newKey = rule.getMatcher().getKey();

    for (MailFilterRuleConfiguration existing : rules) {
      if (existing.getMatcher().getType() != rule.getMatcher().getType() || !sameAction(existing, rule)) continue;
      if (hasKey(existing.getMatcher(), newKey)) {
        if (compacted) save(rules);
        return false; // already learned
      }

      mergeKey(existing.getMatcher(), newKey);
      save(rules);
      return true;
    }

    rules.add(rule);
    save(rules);
    return true;
  }

  /**
   * Merges together rules that already share (matcher type, action) but exist as several
   * separate entries in the list — a file written before this merging existed, or any other
   * mishap, can contain some.
   * @return true if something was merged (i.e. if rules was modified).
   */
  private boolean compact(List<MailFilterRuleConfiguration> rules) {
    boolean changed = false;
    for (int i = 0; i < rules.size(); i++) {
      MailFilterRuleConfiguration keep = rules.get(i);
      for (int j = rules.size() - 1; j > i; j--) {
        MailFilterRuleConfiguration duplicate = rules.get(j);
        if (keep.getMatcher().getType() == duplicate.getMatcher().getType() && sameAction(keep, duplicate)) {
          mergeMatcherKeysInto(keep.getMatcher(), duplicate.getMatcher());
          rules.remove(j);
          changed = true;
        }
      }
    }
    return changed;
  }

  private void mergeMatcherKeysInto(MailFilterRuleMatcherConfiguration target, MailFilterRuleMatcherConfiguration source) {
    if (source.getKeys() != null) {
      source.getKeys().forEach(key -> mergeKey(target, key));
    } else if (source.getKey() != null) {
      mergeKey(target, source.getKey());
    }
  }

  private boolean sameAction(MailFilterRuleConfiguration a, MailFilterRuleConfiguration b) {
    return a.getAction().getType() == b.getAction().getType()
            && Objects.equals(a.getAction().getKey(), b.getAction().getKey());
  }

  private boolean hasKey(MailFilterRuleMatcherConfiguration matcher, String key) {
    if (matcher.getKeys() != null) return matcher.getKeys().contains(key);
    return Objects.equals(matcher.getKey(), key);
  }

  /** Converts key into keys (with the old value inside) if needed, then adds key to it. */
  private void mergeKey(MailFilterRuleMatcherConfiguration matcher, String key) {
    Set<String> keys = matcher.getKeys();
    if (keys == null) {
      keys = new LinkedHashSet<>();
      if (matcher.getKey() != null) keys.add(matcher.getKey());
      matcher.setKey(null);
      matcher.setKeys(keys);
    }
    keys.add(key);
  }
}
