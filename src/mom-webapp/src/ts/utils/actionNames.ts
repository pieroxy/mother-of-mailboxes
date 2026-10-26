import { ActionType, MailFilterRuleActionConfiguration } from "../auto/pieroxy-mom";

/** Mirrors the backend's ActionNames#defaultName, keep both in sync. */
export function defaultActionName(action: MailFilterRuleActionConfiguration): string {
  const key = action.key;
  switch (action.type) {
    case ActionType.MOVE_TO: return "→ " + key;
    case ActionType.MOVE_TO_AND_READ: return "→ " + key + " (read)";
    case ActionType.READ: return "Mark read";
    case ActionType.NOOP: return "No action";
    case ActionType.FORWARD: return "Forward to " + key;
    case ActionType.REDIRECT: return "Redirect to " + key;
    case ActionType.REPLY: return key && key.trim() ? "Reply: " + key.trim() : "Reply";
    case ActionType.AND: return (action.children || []).map(defaultActionName).join(" + ") || "?";
    case ActionType.OR: return (action.children || []).map(defaultActionName).join(" / ") || "?";
    default: return "?";
  }
}

/** Mirrors the backend's ActionNames#nameOf. */
export function actionName(action: MailFilterRuleActionConfiguration): string {
  return action.name && action.name.trim() ? action.name.trim() : defaultActionName(action);
}
