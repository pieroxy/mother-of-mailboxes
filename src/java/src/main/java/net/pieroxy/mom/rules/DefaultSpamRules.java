package net.pieroxy.mom.rules;

import net.pieroxy.mom.config.general.MailFilterRuleActionConfiguration;
import net.pieroxy.mom.config.general.MailFilterRuleConfiguration;
import net.pieroxy.mom.config.general.MailFilterRuleMatcherConfiguration;
import net.pieroxy.mom.rules.actions.ActionType;
import net.pieroxy.mom.rules.matchers.MatcherType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The spam-handling rule set offered when creating an account — the same rules as
 * config.example.json (checked by DefaultSpamRulesTest). Reputation rules referencing a list
 * missing from {@code reputationLists} are dropped (or, for an OR, reduced to the lists present):
 * an unknown list never matches.
 */
public final class DefaultSpamRules {
  private DefaultSpamRules() {
  }

  /**
   * @param classifierFolder receives the subject classifier's verdicts — must be listed in the
   *     account's {@code classifierExcludedFolders} so the classifier never trains on them.
   */
  public static List<MailFilterRuleConfiguration> build(String spamFolder, String classifierFolder, Set<String> availableListIds) {
    List<MailFilterRuleConfiguration> rules = new ArrayList<>();
    rules.add(rule(leaf(MatcherType.SPF_RESULT_EQUALS, "fail"), ActionType.MOVE_TO_AND_READ, spamFolder));
    rules.add(rule(leaf(MatcherType.DKIM_RESULT_EQUALS, "fail"), ActionType.MOVE_TO_AND_READ, spamFolder));
    rules.add(rule(composite(MatcherType.AND,
        leaf(MatcherType.DMARC_RESULT_EQUALS, "fail"),
        leaf(MatcherType.DMARC_POLICY_EQUALS, "reject")), ActionType.MOVE_TO_AND_READ, spamFolder));
    rules.add(rule(composite(MatcherType.AND,
        leaf(MatcherType.DMARC_RESULT_EQUALS, "fail"),
        leafKeys(MatcherType.DMARC_POLICY_EQUALS, "quarantine", "none")), ActionType.MOVE_TO, spamFolder));
    rules.add(rule(composite(MatcherType.AND,
        leaf(MatcherType.SPF_RESULT_EQUALS, "softfail"),
        leafKeys(MatcherType.FCRDNS_RESULT_EQUALS, "fail", "none")), ActionType.MOVE_TO, spamFolder));
    rules.add(rule(leaf(MatcherType.SUBJECT_CLASSIFIER_EQUALS, ">0.99"), ActionType.MOVE_TO_AND_READ, classifierFolder));
    addReputationPair(rules, MatcherType.IP_REPUTATION_EQUALS, "spamhaus-drop", "blocklist-de-mail", spamFolder, availableListIds);
    addReputationPair(rules, MatcherType.FROM_DOMAIN_REPUTATION_EQUALS, "hagezi-tif-mini", "blocklist-project-phishing", spamFolder, availableListIds);
    for (String listId : List.of("disposable-email-domains", "hagezi-nrd7")) {
      if (availableListIds.contains(listId)) {
        rules.add(rule(reputation(MatcherType.FROM_DOMAIN_REPUTATION_EQUALS, listId), ActionType.MOVE_TO, spamFolder));
      }
    }
    return rules;
  }

  /** Both lists agreeing → read; either one alone → left unread for review. */
  private static void addReputationPair(List<MailFilterRuleConfiguration> rules, MatcherType type, String listA, String listB,
                                        String spamFolder, Set<String> availableListIds) {
    List<MailFilterRuleMatcherConfiguration> present = new ArrayList<>();
    for (String listId : List.of(listA, listB)) {
      if (availableListIds.contains(listId)) present.add(reputation(type, listId));
    }
    if (present.size() == 2) {
      rules.add(rule(composite(MatcherType.AND, reputation(type, listA), reputation(type, listB)), ActionType.MOVE_TO_AND_READ, spamFolder));
      rules.add(rule(composite(MatcherType.OR, present.get(0), present.get(1)), ActionType.MOVE_TO, spamFolder));
    } else if (present.size() == 1) {
      rules.add(rule(present.get(0), ActionType.MOVE_TO, spamFolder));
    }
  }

  private static MailFilterRuleConfiguration rule(MailFilterRuleMatcherConfiguration matcher, ActionType actionType, String folder) {
    MailFilterRuleActionConfiguration action = new MailFilterRuleActionConfiguration();
    action.setType(actionType);
    action.setKey(folder);
    MailFilterRuleConfiguration rule = new MailFilterRuleConfiguration();
    rule.setMatcher(matcher);
    rule.setAction(action);
    return rule;
  }

  private static MailFilterRuleMatcherConfiguration leaf(MatcherType type, String key) {
    MailFilterRuleMatcherConfiguration matcher = new MailFilterRuleMatcherConfiguration();
    matcher.setType(type);
    matcher.setKey(key);
    return matcher;
  }

  private static MailFilterRuleMatcherConfiguration leafKeys(MatcherType type, String... keys) {
    MailFilterRuleMatcherConfiguration matcher = new MailFilterRuleMatcherConfiguration();
    matcher.setType(type);
    matcher.setKeys(new LinkedHashSet<>(Arrays.asList(keys)));
    return matcher;
  }

  private static MailFilterRuleMatcherConfiguration reputation(MatcherType type, String listId) {
    MailFilterRuleMatcherConfiguration matcher = leaf(type, ">0.5");
    matcher.setListIds(new LinkedHashSet<>(List.of(listId)));
    return matcher;
  }

  private static MailFilterRuleMatcherConfiguration composite(MatcherType type, MailFilterRuleMatcherConfiguration... children) {
    MailFilterRuleMatcherConfiguration matcher = new MailFilterRuleMatcherConfiguration();
    matcher.setType(type);
    matcher.setChildren(new ArrayList<>(Arrays.asList(children)));
    return matcher;
  }
}
