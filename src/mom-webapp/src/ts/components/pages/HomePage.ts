import m from "mithril";
import { ApiEndpoints } from "../../auto/ApiEndpoints";
import { AccountStatusDto, ReputationListDto } from "../../auto/pieroxy-mom";
import { AbstractPage } from "./AbstractPage";
import { CycleProgressBar } from "../CycleProgressBar";
import { ClassifierTrainingSummary } from "../ClassifierTrainingSummary";
import { StatusIcon, accountDisplayStatus, statusDescription, statusLabel } from "../AccountStatus";
import { Routing } from "../../utils/navigation/Routing";
import { Endpoints } from "../../utils/navigation/Endpoints";
import { formatBytes, formatCount } from "../../utils/format";
import { IconButton } from "../IconButton";
import { RefreshIcon } from "../atoms/icons/RefreshIcon";
import { AddCard } from "../AddCard";
import { Notification, Notifications, NotificationsClass, NotificationsType } from "../../utils/Notifications";

const REFRESH_INTERVAL_MS = 60_000;
const TICK_INTERVAL_MS = 1_000;

export class HomePage extends AbstractPage {
  private accounts: AccountStatusDto[] = [];
  private reputationLists: ReputationListDto[] = [];
  private error: string | undefined;
  private refreshTimer: number | undefined;
  private tickTimer: number | undefined;

  getPageTitle(): string {
    return "MOM";
  }

  oninit() {
    this.refreshData = () => this.loadData();
    this.loadData();
    this.refreshTimer = window.setInterval(() => this.loadData(), REFRESH_INTERVAL_MS);
    // Ticks the progress bars every second without re-fetching that often: they're computed
    // live from timestamps already in hand (see CycleProgressBar).
    this.tickTimer = window.setInterval(() => m.redraw(), TICK_INTERVAL_MS);
  }

  onremove() {
    if (this.refreshTimer !== undefined) window.clearInterval(this.refreshTimer);
    if (this.tickTimer !== undefined) window.clearInterval(this.tickTimer);
  }

  render(): m.Children {
    return m("page.homepage", [
      m(".accounts", [
        this.accounts.map((account) => m(AccountRow, { account })),
        m(AddCard, { layout: "column", label: "Add account", title: "Add a new account",
          onclick: () => Routing.goToScreen(Endpoints.ACCOUNT_CREATE) }),
      ]),
      this.error ? m(".accounts-error.errorMessage", this.error) : null,
      m("h2", "Reputation Lists"),
      this.reputationLists.length === 0
        ? m(".reputationlists-empty", "No reputation lists configured.")
        : m(".reputationlists", this.reputationLists.map((list) => m(ReputationListRow, {
          list,
          onRefreshed: (updated) => {
            this.reputationLists = this.reputationLists.map((l) => (l.id === updated.id ? updated : l));
          },
        }))),
    ]);
  }

  private loadData(): Promise<void> {
    // A transport-level failure (offline, server down, ...) already gets its own generic
    // notification from Api.call — this only needs to keep the page's own inline error in sync.
    return Promise.all([ApiEndpoints.Accounts.call({}), ApiEndpoints.ReputationLists.call({})])
      .then(([accountsOutput, reputationListsOutput]) => {
        this.error = undefined;
        this.accounts = accountsOutput.accounts;
        this.reputationLists = reputationListsOutput.lists;
        m.redraw();
      })
      .catch((err: Error) => {
        this.error = err.message;
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
    const displayStatus = accountDisplayStatus(account);
    return m("a.account.status-" + displayStatus.toLowerCase(), { onclick: () => Routing.goToAccountHome(account.name) }, [
      m(".account-left", [
        m(".account-status", { title: statusDescription(displayStatus) }, [
          m(StatusIcon, { status: displayStatus }),
          m(".account-status-label", statusLabel(displayStatus)),
        ]),
      ]),
      m(".account-details",
        m(".account-name", account.name),
        m(".account-messages-processed", account.messagesProcessed + " processed"),
        m(".account-messages-matched", account.messagesMatched + " matched"),
        m(".account-rules", account.ruleCount + " rule(s), " + account.activeRuleCount + " active"),
        m(CycleProgressBar, {
          lastTimestamp: account.lastCycleCompletedTimestamp,
          nextTimestamp: account.nextScheduledCycleTimestamp,
        }),
        m(ClassifierTrainingSummary, { training: account.classifierTraining }),
        account.active && account.status == 'KO' ? m(".account-error", account.lastErrorTimestamp + ": " + account.lastErrorMessage ) : null
      )
    ]);
  }
}

interface ReputationListRowAttrs {
  list: ReputationListDto;
  onRefreshed: (list: ReputationListDto) => void;
}

/**
 * Status is deliberately computed here, client-side, same as an account's cycle progress bar:
 * the server has no opinion on it (ReputationListsApi only ever reports lastRefreshTimestamp +
 * refreshHours) — OK while the cache is younger than refreshHours, WARNING for one missed cycle
 * (a single blip shouldn't read as broken), KO once it's missed two or more, or if there's no
 * cache at all yet. This also makes it self-healing: the moment a refresh succeeds, the cache's
 * age resets and status recovers on its own, with nothing to reset by hand.
 */
function reputationListStatus(list: ReputationListDto): string {
  const lastMs = list.lastRefreshTimestamp ? Date.parse(list.lastRefreshTimestamp) : NaN;
  if (isNaN(lastMs)) return "KO";
  const ageHours = (Date.now() - lastMs) / 3_600_000;
  if (ageHours < list.refreshHours) return "OK";
  if (ageHours < list.refreshHours * 2) return "WARNING";
  return "KO";
}

/** Tooltip on a reputation list's status icon — see reputationListStatus for the thresholds. */
function reputationListStatusDescription(status: string): string {
  switch (status) {
    case "OK":
      return "Up to date";
    case "WARNING":
      return "One refresh missed — the last downloaded copy is still in use";
    default:
      return "Never downloaded, or several refreshes missed";
  }
}

class ReputationListRow implements m.ClassComponent<ReputationListRowAttrs> {
  private refreshing = false;

  view({ attrs }: m.Vnode<ReputationListRowAttrs>): m.Children {
    const list = attrs.list;
    const status = reputationListStatus(list);
    const lastMs = list.lastRefreshTimestamp ? Date.parse(list.lastRefreshTimestamp) : NaN;
    const nextTimestamp = isNaN(lastMs) ? "" : new Date(lastMs + list.refreshHours * 3_600_000).toISOString();

    return m(".reputationlist.status-" + status.toLowerCase(), [
      m(".reputationlist-left", { title: reputationListStatusDescription(status) }, m(StatusIcon, { status })),
      m(".reputationlist-details", [
        m(".reputationlist-name", { title: list.url }, list.id),
        m(".reputationlist-meta", list.type + " · score " + list.score + " · " + formatCount(list.itemCount) + " entries · " + formatBytes(list.contentSizeBytes)),
        m(CycleProgressBar, { lastTimestamp: list.lastRefreshTimestamp, nextTimestamp }),
      ]),
      m(IconButton, {
        icon: RefreshIcon,
        label: this.refreshing ? "Refreshing…" : "Refresh",
        title: this.refreshing ? "Downloading…" : "Download this list again now",
        busy: this.refreshing,
        onclick: () => this.refresh(attrs),
      }),
    ]);
  }

  private refresh(attrs: ReputationListRowAttrs) {
    this.refreshing = true;
    ApiEndpoints.RefreshReputationList.call({ id: attrs.list.id })
      .then((output) => {
        attrs.onRefreshed(output.list);
        Notifications.addNotification(output.error
          ? new Notification(NotificationsClass.REPUTATION_LIST_REFRESHED, NotificationsType.ERROR,
            "Could not refresh " + attrs.list.id + ": " + output.error, 10)
          : new Notification(NotificationsClass.REPUTATION_LIST_REFRESHED, NotificationsType.SUCCESS,
            attrs.list.id + " refreshed.", 3));
      })
      .catch((err: Error) => {
        Notifications.addNotification(new Notification(NotificationsClass.REPUTATION_LIST_REFRESHED, NotificationsType.ERROR,
          "Could not refresh " + attrs.list.id + ": " + err.message, 10));
      })
      .finally(() => {
        this.refreshing = false;
        m.redraw();
      });
  }
}
