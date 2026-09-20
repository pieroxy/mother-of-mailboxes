package net.pieroxy.imf.rules.actions;

import net.pieroxy.imf.config.general.MailFilterRuleActionConfiguration;
import net.pieroxy.imf.rules.actions.implementations.AndAction;
import net.pieroxy.imf.rules.actions.implementations.MoveToAction;
import net.pieroxy.imf.rules.actions.implementations.NoopAction;
import net.pieroxy.imf.rules.actions.implementations.OrAction;
import net.pieroxy.imf.rules.actions.implementations.ReadAction;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum ActionType {
  MOVE_TO(MoveToAction::new, true),
  READ(ReadAction::new, false),
  /**
   * No dedicated class: this is an AND(MOVE_TO, READ) built on the fly, to stay learnable
   * (composites themselves aren't) without duplicating MoveToAction/ReadAction.
   */
  MOVE_TO_AND_READ(() -> new AndAction() {
    @Override
    public void setConfig(MailFilterRuleActionConfiguration config) {
      super.setConfig(config);

      MailFilterRuleActionConfiguration moveToConfig = new MailFilterRuleActionConfiguration();
      moveToConfig.setType(MOVE_TO);
      moveToConfig.setKey(config.getKey());
      moveToConfig.setLogLevel(config.getLogLevel());

      MailFilterRuleActionConfiguration readConfig = new MailFilterRuleActionConfiguration();
      readConfig.setType(READ);
      readConfig.setLogLevel(config.getLogLevel());

      // READ before MOVE_TO: MoveToAction copies the message with its current flags, so \Seen
      // must already be set at copy time to end up on the message in the target folder (setting
      // it after the copy would only affect the source, which is about to be deleted).
      setChildren(Arrays.asList(Action.build(readConfig), Action.build(moveToConfig)));
    }
  }, true),
  AND(AndAction::new, false),
  OR(OrAction::new, false),
  // Not learnable: no folder/key to speak of, and "learn me a rule that does nothing" makes no
  // sense as a by-example action.
  NOOP(NoopAction::new, false);

  private final ActionProvider provider;
  private final boolean learnable;

  ActionType(ActionProvider provider, boolean learnable) {
    this.provider = provider;
    this.learnable = learnable;
  }

  public Action getImplementation() {
    return provider.getAction();
  }

  /**
   * "Leaf" types for which learning a rule by example (imf-rules/ folders) makes sense.
   * Composites (AND/OR) are excluded: reserved for manual config.
   */
  public static List<ActionType> learnableValues() {
    return Arrays.stream(values()).filter(t -> t.learnable).collect(Collectors.toList());
  }
}

interface ActionProvider {
  Action getAction();
}
