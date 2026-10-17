import m from "mithril";
import { AccountStatusDto } from "../auto/pieroxy-mom";
import { StatusOkIcon } from "./atoms/icons/StatusOkIcon";
import { StatusErrorIcon } from "./atoms/icons/StatusErrorIcon";
import { StatusInfoIcon } from "./atoms/icons/StatusInfoIcon";
import { StatusWarningIcon } from "./atoms/icons/StatusWarningIcon";
import { StatusPausedIcon } from "./atoms/icons/StatusPausedIcon";

/**
 * A paused account's displayed status always wins over whatever it last did while running —
 * there's no "last known status" worth showing once the user has turned it off on purpose.
 */
export function accountDisplayStatus(account: AccountStatusDto): string {
  return account.active ? account.status : "PAUSED";
}

interface StatusIconAttrs {
  status: string;
}

export class StatusIcon implements m.ClassComponent<StatusIconAttrs> {
  view({ attrs }: m.Vnode<StatusIconAttrs>): m.Children {
    switch (attrs.status) {
      case "OK":
        return m(StatusOkIcon);
      case "WARNING":
        return m(StatusWarningIcon);
      case "KO":
        return m(StatusErrorIcon);
      case "PAUSED":
        return m(StatusPausedIcon);
      default:
        return m(StatusInfoIcon);
    }
  }
}

/** Tooltip on an account's status icon. */
export function statusDescription(status: string): string {
  switch (status) {
    case "OK":
      return "Waiting for new mail";
    case "PROCESSING":
      return "Processing mail right now";
    case "KO":
      return "The last cycle failed — see the error message";
    case "PAUSED":
      return "Paused: no mail is processed until the account is resumed";
    default:
      return status;
  }
}

/** The word shown under an account's status icon — see {@link StatusIcon} for the matching icon. */
export function statusLabel(status: string): string {
  switch (status) {
    case "OK":
      return "OK";
    case "PROCESSING":
      return "Processing";
    case "KO":
      return "Error";
    case "PAUSED":
      return "Paused";
    default:
      return status;
  }
}
