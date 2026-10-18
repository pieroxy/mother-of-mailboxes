import m from "mithril";
import { ApiEndpoints } from "../../auto/ApiEndpoints";
import { AccountStatusApiOutput, ClassifierTrainingDto } from "../../auto/pieroxy-mom";
import { AbstractPage } from "./AbstractPage";
import { AccountPageHeader } from "../AccountPageHeader";
import { StatusIcon, accountDisplayStatus, statusDescription, statusLabel } from "../AccountStatus";
import { CycleProgressBar } from "../CycleProgressBar";
import { SettingsIcon } from "../atoms/icons/SettingsIcon";
import { StatsIcon } from "../atoms/icons/StatsIcon";
import { LogsIcon } from "../atoms/icons/LogsIcon";
import { DocumentCheckIcon } from "../atoms/icons/DocumentCheckIcon";
import { StatusOkIcon } from "../atoms/icons/StatusOkIcon";
import { StatusInfoIcon } from "../atoms/icons/StatusInfoIcon";
import { Routing } from "../../utils/navigation/Routing";
import { Endpoints } from "../../utils/navigation/Endpoints";
import { accountEditSessions } from "../../utils/AccountEditSession";
import { Notification, Notifications, NotificationsClass, NotificationsType } from "../../utils/Notifications";
import { formatDuration } from "../../utils/format";

const REFRESH_INTERVAL_MS = 60_000;
const TICK_INTERVAL_MS = 1_000;

interface AccountHomePageAttrs {
  accountName: string;
}

interface AccountLink {
  title: string;
  description: string;
  icon: m.ComponentTypes;
  go: (accountName: string) => void;
}

const LINKS: AccountLink[] = [
  {
    title: "Settings", icon: SettingsIcon, go: (accountName) => Routing.goToAccountSettings(accountName),
    description: "Configure the IMAP and SMTP connections, rules, shortcuts and learned rules.",
  },
  {
    title: "Stats", icon: StatsIcon, go: (accountName) => Routing.goToStats(accountName),
    description: "See how many messages were processed and matched per day, and which matchers fire the most.",
  },
  {
    title: "Logs", icon: LogsIcon, go: (accountName) => Routing.goToLogs(accountName),
    description: "Browse the decision log to find out why a given message ended up where it did.",
  },
];

const TOOLS: AccountLink[] = [
  {
    title: "Check delivery settings", icon: DocumentCheckIcon, go: (accountName) => Routing.goToDeliveryCheck(accountName),
    description: "Check that your domain and mail server are set up so the mail you send gets delivered: SPF for now, more to come.",
  },
];

/**
 * An account's hub, reached by clicking its card on the home page: links to its settings, stats
 * and decision log pages, then a detailed view of what the card summarizes (status, cycle timing,
 * counters, classifier training, connection), with the pause/resume toggle, then its tools (see
 * DeliveryCheckPage).
 */
export class AccountHomePage extends AbstractPage<AccountHomePageAttrs> {
  private accountName = "";
  private data: AccountStatusApiOutput | undefined;
  private error: string | undefined;
  private togglingActive = false;
  private refreshTimer: number | undefined;
  private tickTimer: number | undefined;

  getPageTitle(): string {
    return "MOM - " + this.accountName;
  }

  oninit({ attrs }: m.Vnode<AccountHomePageAttrs>) {
    this.accountName = attrs.accountName;
    this.refreshData = () => this.loadData();
    this.loadData();
    this.refreshTimer = window.setInterval(() => this.loadData(), REFRESH_INTERVAL_MS);
    // Same as HomePage: ticks the progress bar every second from timestamps already in hand.
    this.tickTimer = window.setInterval(() => m.redraw(), TICK_INTERVAL_MS);
  }

  onremove() {
    if (this.refreshTimer !== undefined) window.clearInterval(this.refreshTimer);
    if (this.tickTimer !== undefined) window.clearInterval(this.tickTimer);
  }

  render(): m.Children {
    return m("page.accounthomepage", [
      m(AccountPageHeader, { accountName: this.accountName, current: "home" }),
      m(".account-home-links", LINKS.map((link) => this.renderLink(link))),
      this.error ? m(".account-home-error.errorMessage", this.error) : null,
      this.data ? this.renderInformation(this.data) : (this.error ? null : m(".page-loading", "Loading…")),
      m("h2", "Tools"),
      m(".account-home-links", TOOLS.map((link) => this.renderLink(link))),
      this.data && !this.data.mailDiagnosticsConfigured
        ? m("p.field-hint.account-home-tools-hint", [
          "Upcoming checks that send a test message will need an SMTP sink, set in ",
          m("a", { onclick: () => Routing.goToScreen(Endpoints.GENERAL_SETTINGS) }, "General settings"),
          ".",
        ])
        : null,
    ]);
  }

  private renderLink(link: AccountLink): m.Children {
    return m("a.account-home-link", { onclick: () => link.go(this.accountName) }, [
      m(link.icon),
      m(".account-home-link-text", [
        m(".account-home-link-title", link.title),
        m(".account-home-link-description", link.description),
      ]),
    ]);
  }

  private renderInformation(data: AccountStatusApiOutput): m.Children {
    const account = data.account;
    const displayStatus = accountDisplayStatus(account);
    const matchRate = account.messagesProcessed > 0
      ? (account.messagesMatched / account.messagesProcessed * 100).toFixed(1) + "%"
      : "—";
    const training = account.classifierTraining;
    const collected = training ? Math.min(training.spamExamples, training.hamExamples) : 0;

    return [
      m("h2", "Information"),
      m(".account-home-info", [
        m(".page-card.account-home-status.status-" + displayStatus.toLowerCase(), [
          m("h2", "Status"),
          m(".account-home-status-line", [
            m(StatusIcon, { status: displayStatus }),
            m(".account-home-status-text", [
              m(".account-home-status-label", statusLabel(displayStatus)),
              m(".field-hint", statusDescription(displayStatus)),
            ]),
          ]),
          account.active && account.status == "KO"
            ? m(".account-error", formatTimestamp(account.lastErrorTimestamp) + ": " + account.lastErrorMessage)
            : null,
          m("p.field-hint", account.active
            ? "Pausing this account stops it without deleting it or any of its settings."
            : "This account won't process any mail until resumed."),
          m("button", { onclick: () => this.toggleActive(), disabled: this.togglingActive },
            this.togglingActive ? (account.active ? "Pausing…" : "Resuming…") : (account.active ? "Pause Account" : "Resume Account")),
        ]),
        m(".page-card", [
          m("h2", "Cycles"),
          m(CycleProgressBar, {
            lastTimestamp: account.lastCycleCompletedTimestamp,
            nextTimestamp: account.nextScheduledCycleTimestamp,
          }),
          infoRows([
            ["Last cycle", formatTimestamp(account.lastCycleCompletedTimestamp)],
            ["Next cycle", formatTimestamp(account.nextScheduledCycleTimestamp)],
            ["Runs every", formatDuration(data.runEvery)],
          ]),
        ]),
        m(".page-card", [
          m("h2", "Activity"),
          m("p.field-hint", "Since the account last started (counters reset on restart, pause or settings change)."),
          infoRows([
            ["Processed", account.messagesProcessed.toLocaleString()],
            ["Matched", account.messagesMatched.toLocaleString()],
            ["Match rate", matchRate],
            ["Rules", account.ruleCount + " (" + account.activeRuleCount + " active)"],
          ]),
        ]),
        training ? m(".page-card", [
          m("h2", "Spam classifiers"),
          m("p.field-hint", training.spamExamples.toLocaleString() + " spam / " + training.hamExamples.toLocaleString()
            + " ham example(s) collected, shared by all three classifiers."),
          m(".account-home-classifiers", [
            m(ClassifierRow, { label: "Subject", state: training.subject, collected }),
            m(ClassifierRow, { label: "Header", state: training.header, collected }),
            m(ClassifierRow, { label: "Body", state: training.body, collected }),
          ]),
        ]) : null,
        m(".page-card", [
          m("h2", "Connection"),
          infoRows([
            ["IMAP server", data.imapHost + ":" + data.imapPort],
            ["SMTP server", data.smtpHost ? data.smtpHost + ":" + data.smtpPort : "Not configured (this account can't send mail)"],
            data.smtpHost ? ["From address", data.smtpFromAddress || "—"] : null,
          ]),
        ]),
      ]),
    ];
  }

  private loadData() {
    ApiEndpoints.AccountStatus.call({ accountName: this.accountName })
      .then((output) => {
        this.error = undefined;
        this.data = output;
        m.redraw();
      })
      .catch((err: Error) => {
        this.error = err.message;
        m.redraw();
      });
  }

  private toggleActive() {
    if (!this.data) return;
    const active = !this.data.account.active;
    this.togglingActive = true;
    this.error = undefined;
    ApiEndpoints.SetAccountActive.call({ accountName: this.accountName, active })
      .then(() => {
        this.togglingActive = false;
        accountEditSessions.setActive(this.accountName, active);
        Notifications.addNotification(new Notification(NotificationsClass.ACCOUNT_ACTIVE_TOGGLED, NotificationsType.SUCCESS,
          active ? "Account resumed." : "Account paused.", 3));
        this.loadData();
      })
      .catch((err: Error) => {
        this.togglingActive = false;
        this.error = err.message;
        m.redraw();
      });
  }
}

/** Label/value pairs as a two-column list; null entries are skipped. */
function infoRows(rows: ([string, string] | null)[]): m.Children {
  return m("dl.account-home-info-rows", rows.filter((row) => row !== null).map(([label, value]) => [
    m("dt", label),
    m("dd", value),
  ]));
}

/** An ISO-8601 timestamp in the browser's locale and time zone, or "—" if there's none. */
function formatTimestamp(iso: string): string {
  if (!iso) return "—";
  const date = new Date(iso);
  return isNaN(date.getTime()) ? iso : date.toLocaleString();
}

interface ClassifierRowAttrs {
  label: string;
  state: ClassifierTrainingDto;
  collected: number;
}

/** One classifier's training status, spelled out — the account card's ClassifierTrainingSummary badge, in long form. */
class ClassifierRow implements m.ClassComponent<ClassifierRowAttrs> {
  view({ attrs }: m.Vnode<ClassifierRowAttrs>): m.Children {
    const { label, state, collected } = attrs;
    return m(".account-home-classifier" + (state.trained ? ".trained" : ".pending"), [
      m(state.trained ? StatusOkIcon : StatusInfoIcon),
      m(".account-home-classifier-label", label),
      m(".account-home-classifier-status", state.trained
        ? "Trained " + formatTimestamp(state.lastTrainedTimestamp)
        : "Not trained yet: " + collected + "/" + state.minExamplesPerClass + " examples of each class"),
    ]);
  }
}
