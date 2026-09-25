import m from "mithril";
import { ApiEndpoints } from "../../auto/ApiEndpoints";
import { AccountStatusDto } from "../../auto/pieroxy-mom";
import { Auth } from "../../utils/Auth";
import { Endpoints } from "../../utils/navigation/Endpoints";
import { Routing } from "../../utils/navigation/Routing";
import { AbstractPage } from "./AbstractPage";
import { StatusOkIcon } from "../atoms/icons/StatusOkIcon";
import { StatusErrorIcon } from "../atoms/icons/StatusErrorIcon";
import { StatusInfoIcon } from "../atoms/icons/StatusInfoIcon";
import { Notification, Notifications, NotificationsClass, NotificationsType } from "../../utils/Notifications";

const REFRESH_INTERVAL_MS = 60_000;

export class HomePage extends AbstractPage {
  private accounts: AccountStatusDto[] = [];
  private error: string | undefined;
  private refreshTimer: number | undefined;

  getPageTitle(): string {
    return "MOM";
  }

  oninit() {
    this.refreshData = () => this.loadAccounts();
    this.loadAccounts();
    this.refreshTimer = window.setInterval(() => this.loadAccounts(), REFRESH_INTERVAL_MS);
  }

  onremove() {
    if (this.refreshTimer !== undefined) window.clearInterval(this.refreshTimer);
  }

  render(): m.Children {
    return m("homepage", [
      m(".accounts", this.accounts.map((account) => m(AccountRow, { account }))),
      this.error ? m(".accounts-error.errorMessage", this.error) : null,
      m("button.logout-button", { onclick: () => this.logout() }, "Log out"),
    ]);
  }

  private loadAccounts() {
    ApiEndpoints.Accounts.call({})
      .then((output) => {
        this.error = undefined;
        this.accounts = output.accounts;
        m.redraw();
      })
      .catch((err: Error) => {
        this.error = err.message;
        Notifications.addNotification(new Notification(NotificationsClass.SERVER_UNREACHABLE, NotificationsType.ERROR, "Failed to contact server", 5))
        m.redraw();
      });
  }

  private logout() {
    const sessionId = Auth.clearSession();
    Routing.goToScreen(Endpoints.LOGIN);
    if (sessionId) ApiEndpoints.Logout.call({ sessionId });
  }
}

interface AccountRowAttrs {
  account: AccountStatusDto;
}

class AccountRow implements m.ClassComponent<AccountRowAttrs> {
  view({ attrs }: m.Vnode<AccountRowAttrs>): m.Children {
    const account = attrs.account;
    return m(".account.status-" + account.status.toLowerCase(), [
      m(StatusIcon, { status: account.status }),
      m(".account-details", 
        m(".account-name", account.name),
        m(".account-messages-processed", account.messagesProcessed + " processed"),
        m(".account-messages-matched", account.messagesMatched + " matched"),
      )
    ]);
  }
}

interface StatusIconAttrs {
  status: string;
}

class StatusIcon implements m.ClassComponent<StatusIconAttrs> {
  view({ attrs }: m.Vnode<StatusIconAttrs>): m.Children {
    switch (attrs.status) {
      case "OK":
        return m(StatusOkIcon);
      case "KO":
        return m(StatusErrorIcon);
      default:
        return m(StatusInfoIcon);
    }
  }
}
