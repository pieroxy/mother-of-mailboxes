package net.pieroxy.mom.rules.actions;

import net.pieroxy.mom.config.general.MailFilterRuleActionConfiguration;

import java.util.List;
import java.util.stream.Collectors;

/** Names an action for display. Mirrored by the webapp's {@code defaultActionName}, keep both in sync. */
public final class ActionNames {
  private ActionNames() {}

  /** The configured name, or one generated from type and key when unset or blank. */
  public static String nameOf(MailFilterRuleActionConfiguration action) {
    if (action.getName() != null && !action.getName().isBlank()) return action.getName().trim();
    return defaultName(action);
  }

  public static String defaultName(MailFilterRuleActionConfiguration action) {
    String key = action.getKey();
    if (action.getType() == null) return "?";
    return switch (action.getType()) {
      case MOVE_TO -> "→ " + key;
      case MOVE_TO_AND_READ -> "→ " + key + " (read)";
      case READ -> "Mark read";
      case NOOP -> "No action";
      case FORWARD -> "Forward to " + key;
      case REDIRECT -> "Redirect to " + key;
      case REPLY -> key != null && !key.isBlank() ? "Reply: " + key.trim() : "Reply";
      case AND -> joinChildren(action.getChildren(), " + ");
      case OR -> joinChildren(action.getChildren(), " / ");
    };
  }

  private static String joinChildren(List<MailFilterRuleActionConfiguration> children, String separator) {
    if (children == null || children.isEmpty()) return "?";
    return children.stream().map(ActionNames::defaultName).collect(Collectors.joining(separator));
  }
}
