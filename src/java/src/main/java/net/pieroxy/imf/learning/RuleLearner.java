package net.pieroxy.imf.learning;

import net.pieroxy.imf.config.LearningShortcutConfiguration;
import net.pieroxy.imf.config.MailFilterRuleActionConfiguration;
import net.pieroxy.imf.config.MailFilterRuleConfiguration;
import net.pieroxy.imf.config.MailFilterRuleMatcherConfiguration;
import net.pieroxy.imf.utils.mail.ImapMailbox;
import net.pieroxy.imf.rules.actions.Action;
import net.pieroxy.imf.rules.actions.ActionType;
import net.pieroxy.imf.rules.matchers.Matcher;
import net.pieroxy.imf.rules.matchers.MatcherType;
import net.pieroxy.imf.utils.MailTools;

import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Rule by example: the user drops a message into
 * imf-rules/&lt;MATCHER_TYPE&gt;/&lt;ACTION_TYPE&gt;/&lt;key&gt; (e.g. imf-rules/FROM_EQUALS/MOVE_TO/Spam)
 * to create the rule "if &lt;MATCHER_TYPE&gt; extracted from the message matches, then &lt;ACTION_TYPE&gt;(&lt;key&gt;)".
 * Composite types (AND/OR) are excluded: reserved for manual config.
 * <p>
 * {@code learningShortcuts} (see {@link LearningShortcutConfiguration}) offers a second, flatter
 * entry point onto the same mechanism: a single {@code imf-rules/<name>} folder bound to one
 * fixed (matcher type, action) pair, for the handful of combinations actually used day to day —
 * without needing an IMAP client subscribed to the full discovery tree above. The discovery tree
 * itself can be skipped entirely (see the {@code discoveryTreeDisabled} constructor parameter)
 * for a client that shows every folder unconditionally regardless of subscription state, once
 * shortcuts cover what's actually used.
 */
public class RuleLearner {
  private final static Logger LOGGER = Logger.getLogger(RuleLearner.class.getName());
  private final static String ROOT_FOLDER = "imf-rules";
  private final static String DONE_FOLDER = "Done";

  private final ImapMailbox mailbox;
  private final LearnedRulesStore store;
  private final List<LearningShortcutConfiguration> shortcuts;
  private final boolean discoveryTreeDisabled;

  public RuleLearner(ImapMailbox mailbox, LearnedRulesStore store) {
    this(mailbox, store, List.of());
  }

  public RuleLearner(ImapMailbox mailbox, LearnedRulesStore store, List<LearningShortcutConfiguration> shortcuts) {
    this(mailbox, store, shortcuts, false);
  }

  /**
   * @param discoveryTreeDisabled skips the {@code <MATCHER_TYPE>/<ACTION_TYPE>} discovery tree
   *                              entirely (see {@code MailAccountConfiguration.discoveryTreeDisabled})
   *                              — only {@code imf-rules/Done} and any configured shortcut
   *                              folders are created/maintained.
   */
  public RuleLearner(ImapMailbox mailbox, LearnedRulesStore store, List<LearningShortcutConfiguration> shortcuts,
                      boolean discoveryTreeDisabled) {
    this.mailbox = mailbox;
    this.store = store;
    this.shortcuts = shortcuts != null ? shortcuts : List.of();
    this.discoveryTreeDisabled = discoveryTreeDisabled;
    validateShortcuts(this.shortcuts);
  }

  /**
   * Fails fast (rather than silently ignoring a mistake) on anything that would make a shortcut
   * ambiguous or meaningless: an unlearnable type, a matcher key that would never be used (it's
   * always extracted per example, same as the discovery tree), a missing action key (there's no
   * folder level left to carry it, unlike the discovery tree's {@code <key>} folder), a name
   * colliding with the discovery tree's own top-level names, or two shortcuts sharing a name.
   */
  private void validateShortcuts(List<LearningShortcutConfiguration> shortcuts) {
    Set<String> seenNames = new HashSet<>();
    for (LearningShortcutConfiguration shortcut : shortcuts) {
      String name = shortcut.getName();
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("learningShortcuts entry is missing a name");
      }
      if (!seenNames.add(name)) {
        throw new IllegalArgumentException("learningShortcuts name \"" + name + "\" is used more than once");
      }
      if (isReservedName(name)) {
        throw new IllegalArgumentException("learningShortcuts name \"" + name
                + "\" collides with a reserved imf-rules/ folder name");
      }

      MailFilterRuleMatcherConfiguration matcher = shortcut.getMatcher();
      if (matcher == null || matcher.getType() == null) {
        throw new IllegalArgumentException("learningShortcuts \"" + name + "\": matcher.type is required");
      }
      if (!MatcherType.learnableValues().contains(matcher.getType())) {
        throw new IllegalArgumentException("learningShortcuts \"" + name + "\": " + matcher.getType()
                + " is not a learnable matcher type");
      }
      if (matcher.getKey() != null || matcher.getKeys() != null || matcher.getChildren() != null) {
        throw new IllegalArgumentException("learningShortcuts \"" + name
                + "\": matcher must only set type — its key is extracted from each example, same as in imf-rules/"
                + matcher.getType() + "/...");
      }

      MailFilterRuleActionConfiguration action = shortcut.getAction();
      if (action == null || action.getType() == null) {
        throw new IllegalArgumentException("learningShortcuts \"" + name + "\": action.type is required");
      }
      if (!ActionType.learnableValues().contains(action.getType())) {
        throw new IllegalArgumentException("learningShortcuts \"" + name + "\": " + action.getType()
                + " is not a learnable action type");
      }
      if (action.getKey() == null || action.getKey().isBlank()) {
        throw new IllegalArgumentException("learningShortcuts \"" + name + "\": action.key is required"
                + " (there's no <key> folder level to carry it, unlike the discovery tree)");
      }
    }
  }

  private boolean isReservedName(String name) {
    if (name.equals(DONE_FOLDER)) return true;
    for (MatcherType matcherType : MatcherType.learnableValues()) {
      if (name.equals(matcherType.name())) return true;
    }
    return false;
  }

  /** Creates the "ready to use" folder tree (the key level, e.g. "Spam", is left for the user to create). */
  public void ensureFolderSkeleton() throws MessagingException {
    mailbox.getOrCreateFolder(ROOT_FOLDER, DONE_FOLDER);
    if (!discoveryTreeDisabled) {
      for (MatcherType matcherType : MatcherType.learnableValues()) {
        for (ActionType actionType : ActionType.learnableValues()) {
          mailbox.getOrCreateFolder(ROOT_FOLDER, matcherType.name(), actionType.name());
        }
      }
    }
    for (LearningShortcutConfiguration shortcut : shortcuts) {
      mailbox.getOrCreateFolder(ROOT_FOLDER, shortcut.getName());
    }
  }

  /** @return true if at least one new rule was learned during this call. */
  public boolean learnFromExamples() throws MessagingException {
    boolean learnedSomething = false;
    if (!discoveryTreeDisabled) {
      for (MatcherType matcherType : MatcherType.learnableValues()) {
        for (ActionType actionType : ActionType.learnableValues()) {
          Folder actionFolder = mailbox.getOrCreateFolder(ROOT_FOLDER, matcherType.name(), actionType.name());
          for (Folder keyFolder : mailbox.listSubfolders(actionFolder)) {
            learnedSomething |= learnFromFolder(matcherType, actionType, keyFolder.getName(), keyFolder);
          }
        }
      }
    }
    for (LearningShortcutConfiguration shortcut : shortcuts) {
      Folder folder = mailbox.getOrCreateFolder(ROOT_FOLDER, shortcut.getName());
      learnedSomething |= learnFromFolder(shortcut.getMatcher().getType(), shortcut.getAction().getType(),
              shortcut.getAction().getKey(), folder);
    }
    return learnedSomething;
  }

  /**
   * @param actionKey the action's key — the discovery tree derives it from {@code folder}'s own
   *                   name (its "&lt;key&gt;" level), a shortcut takes it straight from its fixed
   *                   config instead (the folder itself is just named after the shortcut).
   */
  private boolean learnFromFolder(MatcherType matcherType, ActionType actionType, String actionKey, Folder folder) throws MessagingException {
    Message[] examples = mailbox.getAllMessages(folder);
    boolean learnedSomething = false;
    try {
      for (Message example : examples) {
        learnedSomething |= learnFromExample(matcherType, actionType, actionKey, example);
      }
    } finally {
      mailbox.closeAndExpunge(folder);
    }
    return learnedSomething;
  }

  private boolean learnFromExample(MatcherType matcherType, ActionType actionType, String actionKey, Message example) {
    try {
      Matcher matcher = matcherType.getImplementation();
      String matcherKey = matcher.extractKeyFromExample(example);

      MailFilterRuleMatcherConfiguration matcherConfig = new MailFilterRuleMatcherConfiguration();
      matcherConfig.setType(matcherType);
      matcherConfig.setKey(matcherKey);

      MailFilterRuleActionConfiguration actionConfig = new MailFilterRuleActionConfiguration();
      actionConfig.setType(actionType);
      actionConfig.setKey(actionKey);

      MailFilterRuleConfiguration ruleConfig = new MailFilterRuleConfiguration();
      ruleConfig.setMatcher(matcherConfig);
      ruleConfig.setAction(actionConfig);

      boolean learned = store.addIfAbsent(ruleConfig);
      if (learned) {
        LOGGER.info("Learned rule: " + matcherType + "(" + matcherKey + ") -> " + actionType + "(" + actionKey + ")");
      }

      Action action = Action.build(actionConfig);
      try {
        boolean result = action.run(example);
        action.getLogger().info(() -> "Action applied (success=" + result + ") to learning example from "
                + MailTools.describeFromSafely(example));
      } catch (Exception e) {
        action.getLogger().log(Level.WARNING, "Action failed on learning example from "
                + MailTools.describeFromSafely(example), e);
      }

      if (!example.isSet(Flags.Flag.DELETED)) {
        // The action neither moved nor deleted the example message: file it away anyway so the
        // same rule isn't relearned in a loop on every cycle.
        moveToDone(example);
      }
      return learned;
    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Failed to learn a rule from example message for " + matcherType + "/" + actionType
              + "(" + actionKey + ")", e);
      return false;
    }
  }

  private void moveToDone(Message example) throws MessagingException {
    // Unread: copyMessages() below copies the message with its current flags, so \Seen must be
    // cleared beforehand to land on the copy — a clear "something here needs sorting" indicator
    // in the mail client, rather than a message silently waiting to be noticed.
    example.setFlag(Flags.Flag.SEEN, false);
    Folder doneFolder = mailbox.getOrCreateFolder(ROOT_FOLDER, DONE_FOLDER);
    example.getFolder().copyMessages(new Message[]{example}, doneFolder);
    example.setFlag(Flags.Flag.DELETED, true);
  }
}
