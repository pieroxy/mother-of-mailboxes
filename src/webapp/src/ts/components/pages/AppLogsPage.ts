import m from "mithril";
import { ApiEndpoints } from "../../auto/ApiEndpoints";
import { AccessLogEntryDto, AppLogEntryDto, AppLogFileDto } from "../../auto/pieroxy-mom";
import { AbstractPage } from "./AbstractPage";
import { Routing } from "../../utils/navigation/Routing";
import { Endpoints } from "../../utils/navigation/Endpoints";
import { formatBytes, todayIsoDate } from "../../utils/format";

type Source = "app" | "access";

interface LevelOption {
  label: string;
  /** java.util.logging level name sent as minLevel; null for any. */
  minLevel: string | null;
  title: string;
}

const LEVEL_OPTIONS: LevelOption[] = [
  { label: "All", minLevel: null, title: "Every entry, debug included" },
  { label: "Info+", minLevel: "INFO", title: "Info, warnings and errors" },
  { label: "Warnings+", minLevel: "WARNING", title: "Warnings and errors" },
  { label: "Errors", minLevel: "SEVERE", title: "Errors only" },
];

interface StatusOption {
  label: string;
  /** 0: no bound. */
  minStatus: number;
  maxStatus: number;
  title: string;
}

const STATUS_OPTIONS: StatusOption[] = [
  { label: "All", minStatus: 0, maxStatus: 0, title: "Every request" },
  { label: "Errors", minStatus: 400, maxStatus: 0, title: "Requests answered with a 4xx or 5xx status" },
  { label: "4xx", minStatus: 400, maxStatus: 499, title: "Client errors: unknown paths, rejected logins, bad requests…" },
  { label: "5xx", minStatus: 500, maxStatus: 0, title: "Server errors" },
];

/** java.util.logging names, as written in the file, to what users know them as. */
const LEVEL_LABELS: Record<string, string> = {
  SEVERE: "ERROR", WARNING: "WARN", INFO: "INFO", CONFIG: "CONFIG", FINE: "DEBUG", FINER: "DEBUG", FINEST: "TRACE",
};

const PAGE_SIZE = 100;
const QUERY_DEBOUNCE_MS = 300;

/**
 * Browses MOM's logs, newest entries first: the application log (log.txt and its rotated archives —
 * see AppLogsApi), a stack trace folded into the entry it belongs to and a separator at each
 * restart; or the web server's access log, one file per day (see AccessLogsApi).
 */
export class AppLogsPage extends AbstractPage {
  private source: Source = "app";
  private files: AppLogFileDto[] = [];
  private file: string | null = null;
  private minLevel: string | null = "INFO";
  private status: StatusOption = STATUS_OPTIONS[0];
  private query = "";

  private appEntries: AppLogEntryDto[] = [];
  private accessEntries: AccessLogEntryDto[] = [];
  private nextCursor: string | null = null;
  private expanded = new Set<AppLogEntryDto | AccessLogEntryDto>();
  private loading = false;
  private hasLoadedOnce = false;
  private error: string | undefined;
  /** Incremented on every new search, so a slower stale response can't overwrite a newer one. */
  private requestId = 0;
  private queryTimer: number | undefined;

  getPageTitle(): string {
    return "MOM - Logs";
  }

  oninit() {
    this.refreshData = () => this.search();
    this.search();
  }

  onremove() {
    window.clearTimeout(this.queryTimer);
  }

  render(): m.Children {
    return m("page.applogspage", [
      m(".page-header", [
        m("a.page-back", { onclick: () => Routing.goToScreen(Endpoints.HOME) }, "‹ Back home"),
        m("h1.page-title", "Logs"),
      ]),
      this.renderFilters(),
      this.error ? m(".stats-error.errorMessage", this.error) : null,
      this.renderEntries(),
    ]);
  }

  private renderFilters(): m.Children {
    return m(".logs-filters", [
      m(".logs-filter-row", [
        this.files.length > 0 ? m("select.applogs-file", {
          value: this.file ?? "",
          title: this.source === "app" ? "Log file: the current one, or an archive rotated at midnight" : "Day",
          onchange: (e: Event) => {
            this.file = (e.target as HTMLSelectElement).value;
            this.search();
          },
        }, this.files.map((f) => m("option", { value: f.id }, this.fileLabel(f)))) : null,
        m("input.logs-search", {
          type: "search", value: this.query,
          placeholder: this.source === "app" ? "Search messages, sources and stack traces" : "Search addresses, paths, user agents…",
          oninput: (e: Event) => this.setQuery((e.target as HTMLInputElement).value),
        }),
      ]),
      m(".logs-chip-groups", [
        m(".logs-chip-group", [
          m(".logs-chip-group-label", "Log"),
          m(".logs-chips", [
            this.chip("Application", "MOM's own log", this.source === "app", () => this.setSource("app")),
            this.chip("Access", "Every HTTP request the web server answered", this.source === "access", () => this.setSource("access")),
          ]),
        ]),
        this.source === "app"
          ? m(".logs-chip-group", [
              m(".logs-chip-group-label", "Level"),
              m(".logs-chips", LEVEL_OPTIONS.map((option) => this.chip(option.label, option.title, this.minLevel === option.minLevel, () => {
                this.minLevel = option.minLevel;
                this.search();
              }))),
            ])
          : m(".logs-chip-group", [
              m(".logs-chip-group-label", "Status"),
              m(".logs-chips", STATUS_OPTIONS.map((option) => this.chip(option.label, option.title, this.status === option, () => {
                this.status = option;
                this.search();
              }))),
            ]),
      ]),
    ]);
  }

  private chip(label: string, title: string, active: boolean, onclick: () => void): m.Children {
    return m("button.logs-chip" + (active ? ".active" : ""), { title, onclick }, label);
  }

  private fileLabel(file: AppLogFileDto): string {
    const size = " (" + formatBytes(file.size) + ")";
    if (this.source === "access") return (file.id === todayIsoDate() ? "Today" : file.id) + size;
    if (file.id === "current") return "Current log" + size;
    return "Until " + new Date(file.lastModified).toLocaleString() + size;
  }

  private renderEntries(): m.Children {
    if (!this.hasLoadedOnce) return this.loading ? m(".page-loading", "Loading…") : null;
    if (this.files.length === 0) {
      return m(".logs-empty", this.source === "app"
        ? "MOM doesn't write a log file: there's nothing to show here."
        : "No access log yet: it's written by the web server, one file per day.");
    }
    const entries: unknown[] = this.source === "app" ? this.appEntries : this.accessEntries;
    // Refetch keeps the previous list (faded) rather than flashing to the loading placeholder.
    return m(".logs-content" + (this.loading ? ".logs-content-loading" : ""), [
      entries.length === 0
        ? m(".logs-empty", this.nextCursor ? "Nothing found yet." : "No entry matches in this file.")
        : this.source === "app"
          ? m(".applogs-list", this.appEntries.map((entry, index) => [
              index === 0 || entry.runId !== this.appEntries[index - 1].runId ? this.renderRunSeparator(entry) : null,
              this.renderAppEntry(entry),
            ]))
          : m(".applogs-list", this.accessEntries.map((entry) => this.renderAccessEntry(entry))),
      this.nextCursor ? m(".logs-more", m("button", { disabled: this.loading, onclick: () => this.loadMore() }, "Load more")) : null,
    ]);
  }

  /** Newest first, so the separator sits above the most recent entry of each run. */
  private renderRunSeparator(entry: AppLogEntryDto): m.Children {
    return m(".applogs-run", { title: "Instance ID: a new one is drawn at every start of MOM" },
      entry.runId ? "Run " + entry.runId : "Unformatted output");
  }

  private renderAppEntry(entry: AppLogEntryDto): m.Children {
    const level = entry.level ? (LEVEL_LABELS[entry.level] ?? entry.level) : "";
    return this.renderEntry(entry, !!entry.details, [
      m("span.applogs-time", entry.timestamp ?? ""),
      m("span.applogs-level.level-" + (entry.level ?? "none").toLowerCase(), { title: entry.level ?? "No level" }, level),
      m("span.applogs-source", { title: entry.source ?? "" }, entry.source ?? ""),
    ], entry.message, () => m("pre.applogs-details", entry.details));
  }

  private renderAccessEntry(entry: AccessLogEntryDto): m.Children {
    if (!entry.status) return this.renderEntry(entry, false, [m("span.applogs-time"), m("span"), m("span")], entry.raw, () => null);
    const statusClass = Math.floor(entry.status / 100) + "xx";
    return this.renderEntry(entry, true, [
      m("span.applogs-time", { title: entry.time }, accessTime(entry.time)),
      m("span.applogs-status.status-" + statusClass, { title: "HTTP status" }, entry.status),
      m("span.applogs-source", { title: entry.remoteAddr + (entry.user !== "-" ? ", logged in as " + entry.user : "") },
        entry.remoteAddr + (entry.user !== "-" ? " (" + entry.user + ")" : "")),
    ], entry.request + (entry.durationMs != null ? "  ·  " + entry.durationMs + " ms" : ""), () => m(".config-grid.applogs-details", [
      detailRow("User", entry.user),
      detailRow("Bytes sent", entry.bytes),
      detailRow("Referer", entry.referer),
      detailRow("User agent", entry.userAgent),
      detailRow("Raw line", entry.raw),
    ]));
  }

  /** The columns before the message, the message (with an expand marker when there's more), and the expanded part. */
  private renderEntry(entry: AppLogEntryDto | AccessLogEntryDto, expandable: boolean, columns: m.Children[], message: string,
                      details: () => m.Children): m.Children {
    const expanded = this.expanded.has(entry);
    return m(".applogs-entry" + (expanded ? ".expanded" : "") + (expandable ? ".expandable" : ""), [
      m(".applogs-entry-summary", {
        title: expandable ? (expanded ? "Hide details" : "Show details") : undefined,
        onclick: expandable ? () => (expanded ? this.expanded.delete(entry) : this.expanded.add(entry)) : undefined,
      }, [
        ...columns,
        m("span.applogs-message", [
          message,
          expandable ? m("span.applogs-more", { title: "Show more" }, expanded ? " ▾" : " ▸") : null,
        ]),
      ]),
      expanded ? details() : null,
    ]);
  }

  private setSource(source: Source) {
    if (source === this.source) return;
    this.source = source;
    this.file = null;
    this.files = [];
    this.search();
  }

  private setQuery(value: string) {
    this.query = value;
    window.clearTimeout(this.queryTimer);
    this.queryTimer = window.setTimeout(() => this.search(), QUERY_DEBOUNCE_MS);
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
    const source = this.source;
    this.loading = true;
    this.error = undefined;
    const call = source === "app"
      ? ApiEndpoints.AppLogs.call({
          file: this.file as string, minLevel: this.minLevel as string, query: this.query, cursor: cursor as string, limit: PAGE_SIZE,
        })
      : ApiEndpoints.AccessLogs.call({
          file: this.file as string, minStatus: this.status.minStatus, maxStatus: this.status.maxStatus, query: this.query,
          cursor: cursor as string, limit: PAGE_SIZE,
        });
    call
      .then((output) => {
        if (requestId !== this.requestId) return;
        this.files = output.files;
        this.file = output.file ?? null;
        if (cursor === null) this.expanded.clear();
        if (source === "app") {
          const entries = output.entries as AppLogEntryDto[];
          this.appEntries = cursor === null ? entries : this.appEntries.concat(entries);
        } else {
          const entries = output.entries as AccessLogEntryDto[];
          this.accessEntries = cursor === null ? entries : this.accessEntries.concat(entries);
        }
        this.nextCursor = output.nextCursor ?? null;
        this.loading = false;
        this.hasLoadedOnce = true;
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

/** "16/Oct/2026:11:48:06 +0200" -> "11:48:06": the day is already the selected file. */
function accessTime(time: string): string {
  const match = /:(\d{2}:\d{2}:\d{2}) /.exec(time);
  return match ? match[1] : time;
}
