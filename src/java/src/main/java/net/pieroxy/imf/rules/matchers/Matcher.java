package net.pieroxy.imf.rules.matchers;

import net.pieroxy.imf.config.general.MailFilterRuleMatcherConfiguration;
import net.pieroxy.imf.utils.logging.LogLevels;
import net.pieroxy.imf.rules.RuleContext;

import javax.mail.Message;
import javax.mail.MessagingException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public abstract class Matcher {
  private MailFilterRuleMatcherConfiguration config;
  private List<Matcher> children = Collections.emptyList();
  private Logger logger = Logger.getLogger(Matcher.class.getName());
  private Set<String> lookupSet;

  /** Equivalent to {@link #build(MailFilterRuleMatcherConfiguration, RuleContext)} with no account context available. */
  public static Matcher build(MailFilterRuleMatcherConfiguration config) {
    return build(config, RuleContext.EMPTY);
  }

  /**
   * Recursively builds the matcher tree described by the config (composite matchers like
   * AND/OR/NOT reference other matchers via their "children"). {@code context} is bound before
   * {@link #setConfig}, not after: a matcher that needs it (e.g. to check right away whether it
   * has a usable model — see {@code SubjectClassifierMatcher}) does that check from within
   * {@code setConfig}, so the context must already be in place by then.
   */
  public static Matcher build(MailFilterRuleMatcherConfiguration config, RuleContext context) {
    Matcher matcher = config.getType().getImplementation();
    matcher.bindContext(context);
    matcher.setConfig(config);
    if (config.getChildren() != null) {
      matcher.children = config.getChildren().stream().map(c -> Matcher.build(c, context)).collect(Collectors.toList());
    }
    matcher.validate();
    return matcher;
  }

  public abstract MatchResult matches(Message message) throws MessagingException;

  /**
   * Structural check once children are set (e.g. a composite requiring an exact child count) —
   * no-op by default. Runs at startup (see {@code RuleCatalog}, built eagerly), not deferred to
   * the first message, so a misconfigured rule fails loudly right away rather than repeatedly on
   * every message inspected.
   */
  protected void validate() {}

  /**
   * Account-level context (see {@link RuleContext}) this matcher was built with — no-op by
   * default, since most matcher types don't need it. Only {@code SubjectClassifierMatcher}/
   * {@code HeaderClassifierMatcher} override this today, to learn which account's model file to
   * load; overriding it rather than reading a field directly is what lets {@link #build} bind it
   * uniformly regardless of matcher type.
   */
  protected void bindContext(RuleContext context) {}

  /**
   * Computes the config key from an example message (rule learning via the imf-rules/
   * folders). Only "leaf" matchers (see {@link net.pieroxy.imf.rules.matchers.MatcherType#learnableValues()})
   * need to override this; composites are never asked for it.
   */
  public String extractKeyFromExample(Message message) throws MessagingException {
    throw new UnsupportedOperationException(getClass().getSimpleName() + " does not support learning a rule from an example message");
  }

  public void setConfig(MailFilterRuleMatcherConfiguration config) {
    this.config = config;
    String name = Matcher.class.getName() + "." + config.getType() + "[" + describeKey() + "]";
    this.logger = Logger.getLogger(name);
    // Default = INFO: WARNING (errors) and INFO (matched) must be visible without explicit
    // configuration; only the DEBUG-level detail of each matching test is an opt-in per node.
    this.logger.setLevel(LogLevels.parse(config.getLogLevel(), Level.INFO));
  }
  protected MailFilterRuleMatcherConfiguration getConfig() {
    return config;
  }
  protected List<Matcher> getChildren() {
    return children;
  }

  /** For logs: the key if key is used, otherwise a summary of the size of keys. */
  protected String describeKey() {
    if (config.getKeys() != null) return config.getKeys().size() + " keys";
    return config.getKey() != null ? config.getKey() : "";
  }

  /**
   * Compact representation of this matcher's tree for startup logs (see
   * {@link net.pieroxy.imf.rules.RuleCatalog#logRules}), e.g. {@code AND(FROM_EQUALS(toto.com),
   * SUBJECT_STARTS_WITH(toto))} or {@code FROM_EQUALS(32 keys)}.
   */
  public String describe() {
    String type = config.getType().name();
    if (!children.isEmpty()) {
      return type + "(" + children.stream().map(Matcher::describe).collect(Collectors.joining(",")) + ")";
    }
    return type + "(" + describeKey() + ")";
  }

  /**
   * Tests candidate against the matcher's config (keys if set, otherwise key), with the
   * supplied comparison function (equals, equalsIgnoreCase...), and returns the configured key
   * that matched (useful for {@link #matched}, in particular when several "keys" are configured
   * and we want to know exactly which one hit). Factors out what would otherwise be duplicated
   * in every "leaf" matcher that learns several keys for the same action (see
   * {@link net.pieroxy.imf.learning.LearnedRulesStore}).
   */
  protected Optional<String> matchingKey(String candidate, BiPredicate<String, String> comparator) {
    if (candidate == null) return Optional.empty();
    if (config.getKeys() != null) {
      return config.getKeys().stream().filter(k -> comparator.test(candidate, k)).findFirst();
    }
    if (config.getKey() != null && comparator.test(candidate, config.getKey())) {
      return Optional.of(config.getKey());
    }
    return Optional.empty();
  }

  /**
   * Precomputes a hash-based lookup set from key/keys, for a "leaf" matcher whose test is a
   * plain equality (case-sensitive or not) — unlike {@link #matchingKey}, which scans every key
   * with an arbitrary comparator (needed by e.g. a "starts with" matcher, whose test isn't
   * equality and so can't be hashed). Call once {@code config} is set (typically by overriding
   * {@link #setConfig}); {@link #matchesKey} then does O(1) instead of {@link #matchingKey}'s
   * O(number of keys) per message.
   */
  protected void initLookupSet(boolean caseInsensitive) {
    Set<String> keys = config.getKeys() != null ? config.getKeys()
        : (config.getKey() != null ? Set.of(config.getKey()) : Set.of());
    if (!caseInsensitive) {
      lookupSet = keys;
      return;
    }
    Set<String> normalized = new HashSet<>(keys.size());
    for (String key : keys) normalized.add(key.toLowerCase());
    lookupSet = normalized;
  }

  /**
   * O(1) counterpart to {@link #matchingKey}, for matchers built via {@link #initLookupSet}.
   * Returns candidate itself when it matches — not necessarily the configured key's original
   * casing (irrelevant here since {@link MatchResult#debugString} is for logs only).
   */
  protected Optional<String> matchesKey(String candidate, boolean caseInsensitive) {
    if (candidate == null || lookupSet == null) return Optional.empty();
    String needle = caseInsensitive ? candidate.toLowerCase() : candidate;
    return lookupSet.contains(needle) ? Optional.of(candidate) : Optional.empty();
  }

  /** A match, with a readable description ("ClassName(detail)") for logs. */
  protected MatchResult matched(String debugDetail) {
    return MatchResult.matched(getClass().getSimpleName() + "(" + debugDetail + ")");
  }

  /**
   * Like {@link #matched(String)}, but with an extra tag right after the class name —
   * {@code "ClassName[tag](detail)"} — for a matcher that picks one among several configured
   * sub-resources (e.g. {@code IpReputationMatcher}/{@code FromDomainReputationMatcher}, whose
   * {@code listIds} can name several reputation lists: {@code tag} says which one actually
   * produced the match).
   */
  protected MatchResult matched(String tag, String debugDetail) {
    return MatchResult.matched(getClass().getSimpleName() + "[" + tag + "](" + debugDetail + ")");
  }

  protected MatchResult notMatched() {
    return MatchResult.notMatched();
  }

  /** Logger specific to this config node, whose level follows its logLevel (INFO by default). */
  public Logger getLogger() {
    return logger;
  }
}
