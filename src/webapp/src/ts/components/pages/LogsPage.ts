import m from "mithril";
import { ApiEndpoints } from "../../auto/ApiEndpoints";
import { DecisionDto, Outcome, RuleOutcomeDto, Trigger } from "../../auto/pieroxy-mom";
import { AbstractPage } from "./AbstractPage";
import { Routing } from "../../utils/navigation/Routing";
import { Endpoints } from "../../utils/navigation/Endpoints";
import { toIsoDate, todayIsoDate } from "../../utils/format";
import { StatusOkIcon } from "../atoms/icons/StatusOkIcon";
import { StatusErrorIcon } from "../atoms/icons/StatusErrorIcon";
import { CloseIcon } from "../atoms/icons/CloseIcon";

/** Route parameter plus optional query parameters (see Routing.goToLogs); Mithril parses "true" into a boolean. */
interface LogsPageAttrs {
  accountName: string;
  from?: string;
  to?: string;
  matcher?: string;
  matcherByType?: boolean | string;
  matcherBlockingOnly?: boolean | string;
}

interface Preset {
  label: string;
  days: number;
}

const PRESETS: Preset[] = [
  { label: "Today", days: 1 },
  { label: "7 days", days: 7 },
  { label: "30 days", days: 30 },
  { label: "90 days", days: 90 },
];

const OUTCOME_LABELS: Record<Outcome, { label: string; title: string }> = {
  [Outcome.HANDLED]: { label: "Handled", title: "A rule matched and stopped evaluation" },
  [Outcome.NOT_HANDLED]: { label: "Not handled", title: "No rule stopped evaluation" },
  [Outcome.LEARNED]: { label: "Learned", title: "A rule was learned from this example" },
  [Outcome.NOT_LEARNED]: { label: "Not learned", title: "Nothing could be learned from this example" },
};

const TRIGGER_LABELS: Record<Trigger, string> = {
  [Trigger.INBOX]: "Inbox",
  [Trigger.LEARNING]: "Learning",
  [Trigger.MANUAL_REPROCESSING]: "Reprocessing",
};

const TRIGGER_TITLES: Record<Trigger, string> = {
  [Trigger.INBOX]: "Messages processed as they arrived in the inbox",
  [Trigger.LEARNING]: "Examples dropped in mom-rules/ to learn a rule from",
  [Trigger.MANUAL_REPROCESSING]: "Messages dropped in mom-rules/ToProcess",
};

const PAGE_SIZE = 50;
const QUERY_DEBOUNCE_MS = 300;

interface MatcherFilter {
  key: string;
  byType: boolean;
  blockingOnly: boolean;
}

/**
 * One account's decision log (see DecisionsApi): every message processed, newest first, each
 * expandable into the rules it went through. Filtering happens server-side, so "Load more" may
 * come back with fewer entries than asked, or none, while there's still older history to search.
 */
export class LogsPage extends AbstractPage<LogsPageAttrs> {
  private accountName = "";
  private from = "";
  private to = "";
  private activePresetDays: number | null = 7;
  private triggers = new Set<Trigger>();
  private outcomes = new Set<Outcome>();
  private errorsOnly = false;
  private query = "";
  private rule: string | null = null;
  /** The rule's name when it has one, for the active-filter pill. */
  private ruleLabel: string | null = null;
  private matcher: MatcherFilter | null = null;

  private entries: DecisionDto[] = [];
  private nextCursor: string | null = null;
  private retentionDays: number | null = null;
  private expanded = new Set<DecisionDto>();
  private loading = false;
  private hasLoadedOnce = false;
  private error: string | undefined;
  /** Incremented on every new search, so a slower stale response can't overwrite a newer one. */
  private requestId = 0;
  private queryTimer: number | undefined;

  getPageTitle(): string {
    return "MOM - Decision log";
  }

  oninit({ attrs }: m.Vnode<LogsPageAttrs>) {
    this.accountName = attrs.accountName;
    this.refreshData = () => this.search();
    if (attrs.matcher) {
      this.matcher = { key: attrs.matcher, byType: isTrue(attrs.matcherByType), blockingOnly: isTrue(attrs.matcherBlockingOnly) };
    }
    if (attrs.from && attrs.to) {
      this.activePresetDays = null;
      this.from = attrs.from;
      this.to = attrs.to;
      this.search();
    } else {
      this.selectPreset(7);
    }
  }

  onremove() {
    window.clearTimeout(this.queryTimer);
  }

  render(): m.Children {
    return m("page.logspage", [
      m(".page-header", [
        m("a.page-back", { onclick: () => Routing.goToScreen(Endpoints.HOME) }, "‹ Back to accounts"),
        m("h1.page-title", this.accountName),
      ]),
      this.retentionDays === 0 ? this.renderDisabledNotice() : null,
      this.renderFilters(),
      this.error ? m(".stats-error.errorMessage", this.error) : null,
      this.renderEntries(),
    ]);
  }

  private renderDisabledNotice(): m.Children {
    return m(".logs-disabled", [
      m("span", "The decision log is disabled for this account: new messages aren't recorded. Entries already on disk are still listed."),
      m("button", { onclick: () => Routing.goToAccountGeneralEdit(this.accountName) }, "Enable"),
    ]);
  }

  private renderFilters(): m.Children {
    return m(".logs-filters", [
      m(".stats-filters", [
        m(".stats-presets", PRESETS.map((preset) => m("button.stats-preset" + (this.activePresetDays === preset.days ? ".active" : ""), {
          onclick: () => this.selectPreset(preset.days),
        }, preset.label))),
        m(".stats-custom-range", [
          m("label", ["From", m("input", {
            type: "date", value: this.from, max: this.to,
            onchange: (e: Event) => this.setRange((e.target as HTMLInputElement).value, this.to),
          })]),
          m("label", ["To", m("input", {
            type: "date", value: this.to, min: this.from, max: todayIsoDate(),
            onchange: (e: Event) => this.setRange(this.from, (e.target as HTMLInputElement).value),
          })]),
        ]),
      ]),
      m(".logs-filter-row", [
        m("input.logs-search", {
          type: "search", value: this.query, placeholder: "Search subject, sender or Message-ID",
          oninput: (e: Event) => this.setQuery((e.target as HTMLInputElement).value),
        }),
      ]),
      m(".logs-chip-groups", [
        this.chipGroup("Outcome", Object.values(Outcome).map((outcome) => this.chip(OUTCOME_LABELS[outcome].label, OUTCOME_LABELS[outcome].title,
          this.outcomes.has(outcome), () => this.toggle(this.outcomes, outcome)))),
        this.chipGroup("Source", Object.values(Trigger).map((trigger) => this.chip(TRIGGER_LABELS[trigger], TRIGGER_TITLES[trigger],
          this.triggers.has(trigger), () => this.toggle(this.triggers, trigger)))),
        m(".logs-chip-group", [
          m(".logs-chip-group-label", "Errors"),
          m(".logs-chips", this.chip("Rule failed", "Only messages where a rule failed", this.errorsOnly, () => {
            this.errorsOnly = !this.errorsOnly;
            this.search();
          })),
        ]),
      ]),
      this.rule || this.matcher ? m(".logs-active-filters", [
        this.rule ? this.removableFilter("Rule matched: " + (this.ruleLabel ?? this.rule), () => (this.rule = null)) : null,
        this.matcher ? this.removableFilter(matcherFilterLabel(this.matcher), () => (this.matcher = null)) : null,
      ]) : null,
    ]);
  }

  /** Chips within a group combine with OR, groups with AND; a group with nothing selected doesn't filter. */
  private chipGroup(label: string, chips: m.Children[]): m.Children {
    return m(".logs-chip-group", [
      m(".logs-chip-group-label", { title: "Any of the selected; none selected shows all" }, label),
      m(".logs-chips", chips),
    ]);
  }

  private chip(label: string, title: string, active: boolean, onclick: () => void): m.Children {
    return m("button.logs-chip" + (active ? ".active" : ""), { title, onclick }, label);
  }

  private removableFilter(label: string, clear: () => void): m.Children {
    return m(".logs-active-filter", [
      m("span", { title: label }, label),
      m("span.logs-filter-remove", { title: "Remove this filter", onclick: () => { clear(); this.search(); } }, m(CloseIcon)),
    ]);
  }

  private renderEntries(): m.Children {
    if (!this.hasLoadedOnce) return this.loading ? m(".page-loading", "Loading…") : null;
    // Refetch keeps the previous list (faded) rather than flashing to the loading placeholder.
    return m(".logs-content" + (this.loading ? ".logs-content-loading" : ""), [
      this.entries.length === 0
        ? m(".logs-empty", this.nextCursor
            ? "Nothing found yet — searched back to " + cursorDay(this.nextCursor) + "."
            : "No decision recorded in this range.")
        : m(".logs-list", this.entries.map((entry) => this.renderEntry(entry))),
      this.nextCursor ? m(".logs-more", [
        this.entries.length > 0 ? m("span.logs-more-hint", "Searched back to " + cursorDay(this.nextCursor)) : null,
        m("button", { disabled: this.loading, onclick: () => this.loadMore() }, "Load more"),
      ]) : null,
    ]);
  }

  private renderEntry(entry: DecisionDto): m.Children {
    const expanded = this.expanded.has(entry);
    const outcome = OUTCOME_LABELS[entry.outcome];
    return m(".logs-entry" + (expanded ? ".expanded" : ""), [
      m(".logs-entry-summary", {
        title: expanded ? "Hide details" : "Show details",
        onclick: () => (expanded ? this.expanded.delete(entry) : this.expanded.add(entry)),
      }, [
        m("span.logs-date", formatDate(entry.date)),
        this.renderOutcomeBadge(entry, outcome),
        entry.hasError
          ? m("span.logs-error-flag", { title: "A rule failed on this message" }, m(StatusErrorIcon))
          : m("span.logs-error-flag"),
        m("span.logs-from", { title: entry.from }, entry.from),
        m("span.logs-subject", { title: entry.subject }, entry.subject),
      ]),
      expanded ? this.renderDetails(entry) : null,
    ]);
  }

  /** A handled message shows the name of the action that took it (absent from entries logged before actions were named). */
  private renderOutcomeBadge(entry: DecisionDto, outcome: { label: string; title: string }): m.Children {
    if (entry.outcome === Outcome.HANDLED && entry.blockingActionName) {
      const kind = entry.blockingSpam ? "Spam filtering" : "Filing";
      return m("span.logs-outcome.outcome-handled" + (entry.blockingSpam ? ".spam" : ""),
        { title: kind + ", by " + (entry.blockingRuleName ?? entry.blockingRule) }, entry.blockingActionName);
    }
    return m("span.logs-outcome.outcome-" + entry.outcome.toLowerCase(), { title: outcome.title }, outcome.label);
  }

  private renderDetails(entry: DecisionDto): m.Children {
    return m(".logs-entry-details", [
      m(".config-grid", [
        detailRow("Trigger", TRIGGER_LABELS[entry.trigger as Trigger] ?? entry.trigger),
        entry.messageId ? detailRow("Message-ID", entry.messageId) : null,
        entry.processedMs != null ? detailRow("Processing time", entry.processedMs + " ms") : null,
        entry.learnedRule ? detailRow("Learned rule", entry.learnedRule) : null,
        entry.reasonForMoveToDone ? detailRow("Moved to Done", entry.reasonForMoveToDone) : null,
      ]),
      entry.rules ? m(".logs-rules", entry.rules.length === 0
        ? m(".logs-empty", "No rule was evaluated.")
        : entry.rules.map((rule) => this.renderRule(rule))) : null,
    ]);
  }

  private renderRule(rule: RuleOutcomeDto): m.Children {
    const blocking = rule.matched === true && rule.keepProcessing === false;
    return m(".logs-rule" + (rule.matched ? ".matched" : ""), [
      m(".logs-rule-status", rule.matched === true
        ? m("span", { title: "Matched" }, m(StatusOkIcon))
        : rule.matched == null
          ? m("span", { title: "Failed before it could match" }, m(StatusErrorIcon))
          : m("span.logs-rule-nomatch", { title: "Didn't match" }, "–")),
      m(".logs-rule-body", [
        m(".logs-rule-header", [
          rule.matched
            ? m("a.logs-rule-name", {
                title: "Show only messages matched by this rule\n" + rule.rule,
                onclick: () => { this.rule = rule.rule; this.ruleLabel = rule.ruleName ?? null; this.search(); },
              }, rule.ruleName ?? rule.rule)
            : m("span.logs-rule-name", { title: rule.rule }, rule.ruleName ?? rule.rule),
          rule.learnedRule ? m("span.logs-tag", { title: "Learned from an example, not configured by hand" }, "learned") : null,
          blocking ? m("span.logs-tag", { title: "Evaluation stopped at this rule" }, "stopped here") : null,
          rule.keepProcessing ? m("span.logs-tag", { title: "keepProcessing: evaluation carried on to the next rule" }, "continued") : null,
        ]),
        rule.actionName ? m(".logs-rule-action" + (rule.spam ? ".spam" : ""),
          { title: rule.spam ? "Action: spam filtering" : "Action: filing" }, rule.actionName) : null,
        rule.matchDetail ? m(".logs-rule-detail", rule.matchDetail) : null,
        rule.exception ? m(".logs-rule-exception", rule.exception) : null,
      ]),
    ]);
  }

  private selectPreset(presetDays: number) {
    this.activePresetDays = presetDays;
    const from = new Date();
    from.setUTCDate(from.getUTCDate() - (presetDays - 1));
    this.from = toIsoDate(from);
    this.to = todayIsoDate();
    this.search();
  }

  private setRange(from: string, to: string) {
    this.activePresetDays = null;
    this.from = from;
    this.to = to;
    this.search();
  }

  private setQuery(value: string) {
    this.query = value;
    window.clearTimeout(this.queryTimer);
    this.queryTimer = window.setTimeout(() => this.search(), QUERY_DEBOUNCE_MS);
  }

  private toggle<T>(set: Set<T>, value: T) {
    set.has(value) ? set.delete(value) : set.add(value);
    this.search();
  }

  /** Restarts from the newest entry with the current filters; the current list stays until the results arrive. */
  private search() {
    this.fetch(null);
  }

  private loadMore() {
    this.fetch(this.nextCursor);
  }

  /** cursor null: a new search, whose results replace the list instead of extending it. */
  private fetch(cursor: string | null) {
    const requestId = ++this.requestId;
    this.loading = true;
    this.error = undefined;
    ApiEndpoints.Decisions.call({
      accountName: this.accountName,
      from: this.from,
      to: this.to,
      triggers: [...this.triggers],
      outcomes: [...this.outcomes],
      errorsOnly: this.errorsOnly,
      rule: this.rule as string,
      matcher: this.matcher?.key as string,
      matcherByType: this.matcher?.byType ?? false,
      matcherBlockingOnly: this.matcher?.blockingOnly ?? false,
      query: this.query,
      cursor: cursor as string,
      limit: PAGE_SIZE,
    })
      .then((output) => {
        if (requestId !== this.requestId) return;
        if (cursor === null) {
          this.entries = output.entries;
          this.expanded.clear();
        } else {
          this.entries = this.entries.concat(output.entries);
        }
        this.hasLoadedOnce = true;
        this.nextCursor = output.nextCursor ?? null;
        this.retentionDays = output.retentionDays;
        this.loading = false;
        m.redraw();
      })
      .catch((err: Error) => {
        if (requestId !== this.requestId) return;
        this.loading = false;
        this.error = err.message;
        m.redraw();
      });
  }
}

function detailRow(label: string, value: string): m.Children {
  return m(".config-row", [m(".config-row-label", label), m(".config-row-value", value)]);
}

function matcherFilterLabel(filter: MatcherFilter): string {
  return (filter.blockingOnly ? "Ended processing: " : "Matcher: ") + filter.key;
}

function isTrue(value: boolean | string | undefined): boolean {
  return value === true || value === "true";
}

/** "yyyy-MM-dd HH:mm:ssZ" (UTC) as local date and time. */
function formatDate(utc: string): string {
  const date = new Date(utc.replace(" ", "T"));
  return isNaN(date.getTime()) ? utc : date.toLocaleString();
}

function cursorDay(cursor: string): string {
  return cursor.split("/")[0];
}
