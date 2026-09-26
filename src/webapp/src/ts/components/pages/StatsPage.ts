import m from "mithril";
import { ApiEndpoints } from "../../auto/ApiEndpoints";
import { DailyStatsDto, MatcherCountDto } from "../../auto/pieroxy-mom";
import { AbstractPage } from "./AbstractPage";
import { Routing } from "../../utils/navigation/Routing";
import { Endpoints } from "../../utils/navigation/Endpoints";
import { DailyStatsChart } from "../DailyStatsChart";
import { TopMatchersChart } from "../TopMatchersChart";

interface StatsPageAttrs {
  accountName: string;
}

interface Preset {
  label: string;
  days: number;
}

const PRESETS: Preset[] = [
  { label: "7 days", days: 7 },
  { label: "14 days", days: 14 },
  { label: "30 days", days: 30 },
  { label: "90 days", days: 90 },
];

/**
 * Per-account stats: a date-range picker (last 7 days by default, presets or a custom range)
 * driving a daily processed/matched bar chart and a top-matchers ranking. No intra-day
 * breakdown — StatsApi only ever returns one bucket per calendar day.
 */
export class StatsPage extends AbstractPage<StatsPageAttrs> {
  private accountName = "";
  private from = "";
  private to = "";
  private activePresetDays: number | null = 7;
  private days: DailyStatsDto[] = [];
  private topMatchers: MatcherCountDto[] = [];
  private topBlockingMatchers: MatcherCountDto[] = [];
  private loading = false;
  private hasLoadedOnce = false;
  private error: string | undefined;
  private showTable = false;

  getPageTitle(): string {
    return "MOM - Stats";
  }

  oninit({ attrs }: m.Vnode<StatsPageAttrs>) {
    this.accountName = attrs.accountName;
    this.selectPreset(7);
  }

  render(): m.Children {
    return m("page.statspage", [
      m(".page-header", [
        m("a.page-back", { onclick: () => Routing.goToScreen(Endpoints.HOME) }, "‹ Back to accounts"),
        m("h1.page-title", this.accountName),
      ]),
      m(".stats-filters", [
        m(".stats-presets", PRESETS.map((preset) => m("button.stats-preset" + (this.activePresetDays === preset.days ? ".active" : ""), {
          onclick: () => this.selectPreset(preset.days),
        }, preset.label))),
        m(".stats-custom-range", [
          m("label", ["From", m("input", {
            type: "date", value: this.from, max: this.to,
            onchange: (e: Event) => this.setFrom((e.target as HTMLInputElement).value),
          })]),
          m("label", ["To", m("input", {
            type: "date", value: this.to, min: this.from, max: todayIso(),
            onchange: (e: Event) => this.setTo((e.target as HTMLInputElement).value),
          })]),
        ]),
      ]),
      this.error ? m(".stats-error.errorMessage", this.error) : null,
      !this.hasLoadedOnce && this.loading ? m(".page-loading", "Loading…") : this.renderContent(),
    ]);
  }

  private renderContent(): m.Children {
    const totalProcessed = this.days.reduce((sum, d) => sum + d.messagesProcessed, 0);
    const totalMatched = this.days.reduce((sum, d) => sum + d.messagesMatched, 0);
    const matchRate = totalProcessed > 0 ? (totalMatched / totalProcessed) * 100 : 0;
    const totalProcessingMs = this.days.reduce((sum, d) => sum + d.avgProcessingMs * d.messagesProcessed, 0);
    const avgMs = totalProcessed > 0 ? totalProcessingMs / totalProcessed : null;

    // Refetch keeps the previous render (faded) rather than flashing to a loading skeleton.
    return m(".stats-content" + (this.loading ? ".stats-content-loading" : ""), [
      m(".stats-summary", [
        m(StatTile, { label: "Processed", value: totalProcessed.toLocaleString() }),
        m(StatTile, { label: "Matched", value: totalMatched.toLocaleString() }),
        m(StatTile, { label: "Match rate", value: matchRate.toFixed(1) + "%" }),
        m(StatTile, { label: "Avg. processing time", value: avgMs !== null ? Math.round(avgMs) + " ms" : "—" }),
      ]),
      m(".page-card", [
        m("h2", "Messages per day"),
        m(DailyStatsChart, { days: this.days }),
        m("button.stats-table-toggle", { onclick: () => (this.showTable = !this.showTable) },
          this.showTable ? "Hide data table" : "Show data table"),
        this.showTable ? this.renderTable() : null,
      ]),
      m(".page-card", [
        m("h2", "Top matchers"),
        m(TopMatchersChart, { allMatchers: this.topMatchers, blockingMatchers: this.topBlockingMatchers }),
      ]),
    ]);
  }

  private renderTable(): m.Children {
    return m("table.stats-table", [
      m("thead", m("tr", [m("th", "Date"), m("th", "Processed"), m("th", "Matched"), m("th", "Match rate")])),
      m("tbody", this.days.map((d) => m("tr", { key: d.date }, [
        m("td", d.date),
        m("td", d.messagesProcessed),
        m("td", d.messagesMatched),
        m("td", d.messagesProcessed > 0 ? ((d.messagesMatched / d.messagesProcessed) * 100).toFixed(1) + "%" : "—"),
      ]))),
    ]);
  }

  private selectPreset(presetDays: number) {
    this.activePresetDays = presetDays;
    // UTC, not local time: StatsLog buckets days by UTC calendar day (see StatsLog#write), so the
    // range picked here must line up with the same boundary or the last/first day could be off by one.
    const to = new Date();
    const from = new Date();
    from.setUTCDate(from.getUTCDate() - (presetDays - 1));
    this.from = toIso(from);
    this.to = toIso(to);
    this.load();
  }

  private setFrom(value: string) {
    this.activePresetDays = null;
    this.from = value;
    this.load();
  }

  private setTo(value: string) {
    this.activePresetDays = null;
    this.to = value;
    this.load();
  }

  private load() {
    this.loading = true;
    this.error = undefined;
    ApiEndpoints.Stats.call({ accountName: this.accountName, from: this.from, to: this.to })
      .then((output) => {
        this.days = output.days;
        this.topMatchers = output.topMatchers;
        this.topBlockingMatchers = output.topBlockingMatchers;
        this.loading = false;
        this.hasLoadedOnce = true;
        m.redraw();
      })
      .catch((err: Error) => {
        this.loading = false;
        this.error = err.message;
        m.redraw();
      });
  }
}

class StatTile implements m.ClassComponent<{ label: string; value: string }> {
  view({ attrs }: m.Vnode<{ label: string; value: string }>): m.Children {
    return m(".stat-tile", [
      m(".stat-tile-value", attrs.value),
      m(".stat-tile-label", attrs.label),
    ]);
  }
}

function toIso(date: Date): string {
  return date.toISOString().slice(0, 10);
}

function todayIso(): string {
  return toIso(new Date());
}
