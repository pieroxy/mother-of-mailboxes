import m from "mithril";
import { ApiEndpoints } from "../../auto/ApiEndpoints";
import { AccountStatusDto } from "../../auto/pieroxy-mom";
import { AbstractPage } from "./AbstractPage";
import { StatusOkIcon } from "../atoms/icons/StatusOkIcon";
import { StatusErrorIcon } from "../atoms/icons/StatusErrorIcon";
import { StatusInfoIcon } from "../atoms/icons/StatusInfoIcon";
import { Notification, Notifications, NotificationsClass, NotificationsType } from "../../utils/Notifications";
import { CycleProgressBar } from "../CycleProgressBar";
import { ClassifierTrainingSummary } from "../ClassifierTrainingSummary";
import { StatsIcon } from "../atoms/icons/StatsIcon";
import { Routing } from "../../utils/navigation/Routing";

const REFRESH_INTERVAL_MS = 60_000;
const TICK_INTERVAL_MS = 1_000;

export class HomePage extends AbstractPage {
  private accounts: AccountStatusDto[] = [];
  private error: string | undefined;
  private refreshTimer: number | undefined;
  private tickTimer: number | undefined;

  getPageTitle(): string {
    return "MOM";
  }

  oninit() {
    this.refreshData = () => this.loadAccounts();
    this.loadAccounts();
    this.refreshTimer = window.setInterval(() => this.loadAccounts(), REFRESH_INTERVAL_MS);
    // Ticks the cycle progress bars every second without re-fetching account data that often:
    // they're computed live from timestamps already in hand (see CycleProgressBar).
    this.tickTimer = window.setInterval(() => m.redraw(), TICK_INTERVAL_MS);
  }

  onremove() {
    if (this.refreshTimer !== undefined) window.clearInterval(this.refreshTimer);
    if (this.tickTimer !== undefined) window.clearInterval(this.tickTimer);
  }

  render(): m.Children {
    return m("page.homepage", [
      m(".accounts", this.accounts.map((account) => m(AccountRow, { account }))),
      this.error ? m(".accounts-error.errorMessage", this.error) : null,
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
}

interface AccountRowAttrs {
  account: AccountStatusDto;
}

class AccountRow implements m.ClassComponent<AccountRowAttrs> {
  view({ attrs }: m.Vnode<AccountRowAttrs>): m.Children {
    const account = attrs.account;
    return m(".account.status-" + account.status.toLowerCase(), [
      m(".account-left", [
        m(StatusIcon, { status: account.status }),
        m("span.stats-link", { title: "View stats", onclick: () => Routing.goToStats(account.name) }, m(StatsIcon)),
      ]),
      m(".account-details",
        m(".account-name", account.name),
        m(".account-messages-processed", account.messagesProcessed + " processed"),
        m(".account-messages-matched", account.messagesMatched + " matched"),
        m(".account-rules", account.ruleCount + " rule(s), " + account.activeRuleCount + " active"),
        m(CycleProgressBar, {
          lastCycleCompletedTimestamp: account.lastCycleCompletedTimestamp,
          nextScheduledCycleTimestamp: account.nextScheduledCycleTimestamp,
        }),
        m(ClassifierTrainingSummary, { training: account.classifierTraining }),
        account.status == 'KO' ? m(".account-error", account.lastErrorTimestamp + ": " + account.lastErrorMessage ) : null
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
