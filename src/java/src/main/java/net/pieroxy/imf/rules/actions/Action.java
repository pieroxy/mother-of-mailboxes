package net.pieroxy.imf.rules.actions;

import net.pieroxy.imf.config.MailFilterRuleActionConfiguration;
import net.pieroxy.imf.utils.logging.LogLevels;
import net.pieroxy.imf.rules.RuleContext;

import javax.mail.Message;
import javax.mail.MessagingException;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public abstract class Action {
  private MailFilterRuleActionConfiguration config;
  private List<Action> children = Collections.emptyList();
  private Logger logger = Logger.getLogger(Action.class.getName());

  /** Equivalent to {@link #build(MailFilterRuleActionConfiguration, RuleContext)} with no account context available. */
  public static Action build(MailFilterRuleActionConfiguration config) {
    return build(config, RuleContext.EMPTY);
  }

  /**
   * Recursively builds the action tree described by the config (composite actions like AND/OR
   * reference other actions via their "children"). {@code context} is bound before
   * {@link #setConfig}, same reasoning as {@code Matcher.build} — no action needs it yet, but the
   * ordering is set up now so a future one (e.g. REPLY/FORWARD, which would need to know the
   * account's own address) can rely on it being available from within {@code setConfig} too.
   */
  public static Action build(MailFilterRuleActionConfiguration config, RuleContext context) {
    Action action = config.getType().getImplementation();
    action.bindContext(context);
    action.setConfig(config);
    if (config.getChildren() != null) {
      action.setChildren(config.getChildren().stream().map(c -> Action.build(c, context)).collect(Collectors.toList()));
    }
    return action;
  }

  public abstract boolean run(Message message) throws MessagingException;

  /**
   * Account-level context (see {@link RuleContext}) this action was built with — no-op by
   * default. No action type overrides this yet; the hook exists for a future one that needs to
   * know something about the account it's running for (e.g. its own address, for REPLY/FORWARD).
   */
  protected void bindContext(RuleContext context) {}

  public void setConfig(MailFilterRuleActionConfiguration config) {
    this.config = config;
    String name = Action.class.getName() + "." + config.getType()
            + (config.getKey() != null ? "[" + config.getKey() + "]" : "");
    this.logger = Logger.getLogger(name);
    // Default = INFO: WARNING (errors) and INFO (action applied) must be visible without any
    // explicit configuration; only the DEBUG-level detail is an opt-in per node.
    this.logger.setLevel(LogLevels.parse(config.getLogLevel(), Level.INFO));
  }
  protected MailFilterRuleActionConfiguration getConfig() {
    return config;
  }
  protected List<Action> getChildren() {
    return children;
  }
  protected void setChildren(List<Action> children) {
    this.children = children;
  }

  /**
   * Compact representation of this action's tree for the startup logs (see
   * {@link net.pieroxy.imf.rules.RuleCatalog#logRules}), e.g. {@code MOVE_TO(Work)} or, for a
   * composite like {@code MOVE_TO_AND_READ}, {@code MOVE_TO_AND_READ(READ(),MOVE_TO(Work))}.
   */
  public String describe() {
    String type = config.getType().name();
    if (!children.isEmpty()) {
      return type + "(" + children.stream().map(Action::describe).collect(Collectors.joining(",")) + ")";
    }
    return type + "(" + (config.getKey() != null ? config.getKey() : "") + ")";
  }

  /** This config node's own logger, whose level follows its logLevel (INFO by default). */
  public Logger getLogger() {
    return logger;
  }
}
