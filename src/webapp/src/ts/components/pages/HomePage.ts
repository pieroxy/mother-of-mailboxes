import m from "mithril";
import { ApiEndpoints } from "../../auto/ApiEndpoints";
import { AccountStatusDto, ReputationListDto } from "../../auto/pieroxy-mom";
import { AbstractPage } from "./AbstractPage";
import { StatusOkIcon } from "../atoms/icons/StatusOkIcon";
import { StatusErrorIcon } from "../atoms/icons/StatusErrorIcon";
import { StatusInfoIcon } from "../atoms/icons/StatusInfoIcon";
import { StatusWarningIcon } from "../atoms/icons/StatusWarningIcon";
import { StatusPausedIcon } from "../atoms/icons/StatusPausedIcon";
import { CycleProgressBar } from "../CycleProgressBar";
import { ClassifierTrainingSummary } from "../ClassifierTrainingSummary";
import { StatsIcon } from "../atoms/icons/StatsIcon";
import { SettingsIcon } from "../atoms/icons/SettingsIcon";
import { Routing } from "../../utils/navigation/Routing";
import { Endpoints } from "../../utils/navigation/Endpoints";
import { formatBytes, formatCount } from "../../utils/format";

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
      m(".accounts", this.accounts.map((account) => m(AccountRow, { account }))),
      m("button.add-account", { onclick: () => Routing.goToScreen(Endpoints.ACCOUNT_CREATE) }, "Add account"),
      this.error ? m(".accounts-error.errorMessage", this.error) : null,
      m("h2", "Reputation Lists"),
      this.reputationLists.length === 0
        ? m(".reputationlists-empty", "No reputation lists configured.")
        : m(".reputationlists", this.reputationLists.map((list) => m(ReputationListRow, { list }))),
    ]);
  }

  private loadData() {
    // A transport-level failure (offline, server down, ...) already gets its own generic
    // notification from Api.call — this only needs to keep the page's own inline error in sync.
    Promise.all([ApiEndpoints.Accounts.call({}), ApiEndpoints.ReputationLists.call({})])
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
    // A paused account's displayed status always wins over whatever it last did while running —
    // there's no "last known status" worth showing once the user has turned it off on purpose.
    const displayStatus = account.active ? account.status : "PAUSED";
    return m(".account.status-" + displayStatus.toLowerCase(), [
      m(".account-left", [
        m(".account-status", [
          m(StatusIcon, { status: displayStatus }),
          m(".account-status-label", statusLabel(displayStatus)),
        ]),
        m(".account-left-actions", [
          m("span.settings-link", { title: "Account settings", onclick: () => Routing.goToAccountSettings(account.name) }, m(SettingsIcon)),
          m("span.stats-link", { title: "View stats", onclick: () => Routing.goToStats(account.name) }, m(StatsIcon)),
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

interface StatusIconAttrs {
  status: string;
}

class StatusIcon implements m.ClassComponent<StatusIconAttrs> {
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

/** The word shown under an account's status icon — see {@link StatusIcon} for the matching icon. */
function statusLabel(status: string): string {
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

interface ReputationListRowAttrs {
  list: ReputationListDto;
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

class ReputationListRow implements m.ClassComponent<ReputationListRowAttrs> {
  view({ attrs }: m.Vnode<ReputationListRowAttrs>): m.Children {
    const list = attrs.list;
    const status = reputationListStatus(list);
    const lastMs = list.lastRefreshTimestamp ? Date.parse(list.lastRefreshTimestamp) : NaN;
    const nextTimestamp = isNaN(lastMs) ? "" : new Date(lastMs + list.refreshHours * 3_600_000).toISOString();

    return m(".reputationlist.status-" + status.toLowerCase(), [
      m(".reputationlist-left", m(StatusIcon, { status })),
      m(".reputationlist-details", [
        m(".reputationlist-name", { title: list.url }, list.id),
        m(".reputationlist-meta", list.type + " · score " + list.score + " · " + formatCount(list.itemCount) + " entries · " + formatBytes(list.contentSizeBytes)),
        m(CycleProgressBar, { lastTimestamp: list.lastRefreshTimestamp, nextTimestamp }),
      ]),
    ]);
  }
}
