import m from "mithril";
import { ApiEndpoints } from "../../auto/ApiEndpoints";
import { AppLogEntryDto, AppLogFileDto } from "../../auto/pieroxy-mom";
import { AbstractPage } from "./AbstractPage";
import { Routing } from "../../utils/navigation/Routing";
import { Endpoints } from "../../utils/navigation/Endpoints";
import { formatBytes } from "../../utils/format";

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

/** java.util.logging names, as written in the file, to what users know them as. */
const LEVEL_LABELS: Record<string, string> = {
  SEVERE: "ERROR", WARNING: "WARN", INFO: "INFO", CONFIG: "CONFIG", FINE: "DEBUG", FINER: "DEBUG", FINEST: "TRACE",
};

const PAGE_SIZE = 100;
const QUERY_DEBOUNCE_MS = 300;

/**
 * Browses MOM's own log (log.txt and its rotated archives — see AppLogsApi): newest entries first,
 * a stack trace folded into the entry it belongs to, and a separator wherever the instance ID
 * changes, i.e. at each restart.
 */
export class AppLogsPage extends AbstractPage {
  private files: AppLogFileDto[] = [];
  private file: string | null = null;
  private minLevel: string | null = "INFO";
  private query = "";

  private entries: AppLogEntryDto[] = [];
  private nextCursor: string | null = null;
  private expanded = new Set<AppLogEntryDto>();
  private loading = false;
  private hasLoadedOnce = false;
  private error: string | undefined;
  /** Incremented on every new search, so a slower stale response can't overwrite a newer one. */
  private requestId = 0;
  private queryTimer: number | undefined;

  getPageTitle(): string {
    return "MOM - Application logs";
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
        m("h1.page-title", "Application logs"),
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
          title: "Log file: the current one, or an archive rotated at midnight",
          onchange: (e: Event) => {
            this.file = (e.target as HTMLSelectElement).value;
            this.search();
          },
        }, this.files.map((f) => m("option", { value: f.id }, fileLabel(f)))) : null,
        m("input.logs-search", {
          type: "search", value: this.query, placeholder: "Search messages, sources and stack traces",
          oninput: (e: Event) => this.setQuery((e.target as HTMLInputElement).value),
        }),
      ]),
      m(".logs-chip-groups", m(".logs-chip-group", [
        m(".logs-chip-group-label", "Level"),
        m(".logs-chips", LEVEL_OPTIONS.map((option) => m("button.logs-chip" + (this.minLevel === option.minLevel ? ".active" : ""), {
          title: option.title,
          onclick: () => {
            this.minLevel = option.minLevel;
            this.search();
          },
        }, option.label))),
      ])),
    ]);
  }

  private renderEntries(): m.Children {
    if (!this.hasLoadedOnce) return this.loading ? m(".page-loading", "Loading…") : null;
    if (this.files.length === 0) return m(".logs-empty", "MOM doesn't write a log file: there's nothing to show here.");
    // Refetch keeps the previous list (faded) rather than flashing to the loading placeholder.
    return m(".logs-content" + (this.loading ? ".logs-content-loading" : ""), [
      this.entries.length === 0
        ? m(".logs-empty", this.nextCursor ? "Nothing found yet." : "No entry matches in this file.")
        : m(".applogs-list", this.entries.map((entry, index) => [
            index === 0 || entry.runId !== this.entries[index - 1].runId ? this.renderRunSeparator(entry) : null,
            this.renderEntry(entry),
          ])),
      this.nextCursor ? m(".logs-more", m("button", { disabled: this.loading, onclick: () => this.loadMore() }, "Load more")) : null,
    ]);
  }

  /** Newest first, so the separator sits above the most recent entry of each run. */
  private renderRunSeparator(entry: AppLogEntryDto): m.Children {
    return m(".applogs-run", { title: "Instance ID: a new one is drawn at every start of MOM" },
      entry.runId ? "Run " + entry.runId : "Unformatted output");
  }

  private renderEntry(entry: AppLogEntryDto): m.Children {
    const expandable = !!entry.details;
    const expanded = this.expanded.has(entry);
    const level = entry.level ? (LEVEL_LABELS[entry.level] ?? entry.level) : "";
    return m(".applogs-entry" + (expanded ? ".expanded" : "") + (expandable ? ".expandable" : ""), [
      m(".applogs-entry-summary", {
        title: expandable ? (expanded ? "Hide details" : "Show details") : undefined,
        onclick: expandable ? () => (expanded ? this.expanded.delete(entry) : this.expanded.add(entry)) : undefined,
      }, [
        m("span.applogs-time", entry.timestamp ?? ""),
        m("span.applogs-level.level-" + (entry.level ?? "none").toLowerCase(), { title: entry.level ?? "No level" }, level),
        m("span.applogs-source", { title: entry.source ?? "" }, entry.source ?? ""),
        m("span.applogs-message", [
          entry.message,
          expandable ? m("span.applogs-more", { title: "Has a stack trace or more lines" }, expanded ? " ▾" : " ▸") : null,
        ]),
      ]),
      expanded ? m("pre.applogs-details", entry.details) : null,
    ]);
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
    this.loading = true;
    this.error = undefined;
    ApiEndpoints.AppLogs.call({
      file: this.file as string,
      minLevel: this.minLevel as string,
      query: this.query,
      cursor: cursor as string,
      limit: PAGE_SIZE,
    })
      .then((output) => {
        if (requestId !== this.requestId) return;
        this.files = output.files;
        this.file = output.file ?? null;
        if (cursor === null) {
          this.entries = output.entries;
          this.expanded.clear();
        } else {
          this.entries = this.entries.concat(output.entries);
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

function fileLabel(file: AppLogFileDto): string {
  const size = formatBytes(file.size);
  if (file.id === "current") return "Current log (" + size + ")";
  return "Until " + new Date(file.lastModified).toLocaleString() + " (" + size + ")";
}
