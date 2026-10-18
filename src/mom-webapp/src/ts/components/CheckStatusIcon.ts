import m from "mithril";
import { CheckStatus } from "../auto/pieroxy-mom";
import { StatusOkIcon } from "./atoms/icons/StatusOkIcon";
import { StatusWarningIcon } from "./atoms/icons/StatusWarningIcon";
import { StatusErrorIcon } from "./atoms/icons/StatusErrorIcon";
import { StatusInfoIcon } from "./atoms/icons/StatusInfoIcon";
import { StatusPausedIcon } from "./atoms/icons/StatusPausedIcon";
import { StatusInProgressIcon } from "./atoms/icons/StatusInProgressIcon";

interface CheckStatusIconAttrs {
  status: CheckStatus;
}

/** The icon of a delivery check's (or finding's) status — see CheckStatus. */
export class CheckStatusIcon implements m.ClassComponent<CheckStatusIconAttrs> {
  view({ attrs }: m.Vnode<CheckStatusIconAttrs>): m.Children {
    switch (attrs.status) {
      case CheckStatus.OK:
        return m(StatusOkIcon);
      case CheckStatus.WARNING:
        return m(StatusWarningIcon);
      case CheckStatus.ERROR:
        return m(StatusErrorIcon);
      case CheckStatus.SKIPPED:
        return m(StatusPausedIcon);
      case CheckStatus.IN_PROGRESS:
        return m(StatusInProgressIcon);
      default:
        return m(StatusInfoIcon);
    }
  }
}

export function checkStatusLabel(status: CheckStatus): string {
  switch (status) {
    case CheckStatus.OK:
      return "OK";
    case CheckStatus.WARNING:
      return "Warning";
    case CheckStatus.ERROR:
      return "Error";
    case CheckStatus.SKIPPED:
      return "Skipped";
    case CheckStatus.IN_PROGRESS:
      return "In progress";
    default:
      return "Info";
  }
}
