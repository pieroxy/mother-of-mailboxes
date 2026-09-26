import m from "mithril";
import { DailyStatsDto } from "../auto/pieroxy-mom";

interface DailyStatsChartAttrs {
  days: DailyStatsDto[];
}

// Categorical slots 1 (blue) & 2 (orange) of the validated dark-mode palette — adjacent-pair CVD
// Delta E 26.8, normal-vision Delta E 31.8, both against this app's #3f1933 card surface (see
// dataviz skill's palette.md; re-validated for this surface rather than assumed).
const PROCESSED_COLOR = "#3987e5";
const MATCHED_COLOR = "#d95926";

const PLOT_WIDTH = 900;
const PLOT_HEIGHT = 160;
const TOP_PADDING = 10;
const LABEL_HEIGHT = 22;
const VIEWBOX_HEIGHT = TOP_PADDING + PLOT_HEIGHT + LABEL_HEIGHT;
const BAR_RADIUS = 3;
const MAX_X_LABELS = 8;

/**
 * Grouped bar chart (inline SVG, no charting library): one group per day, a blue bar for messages
 * processed and an orange bar for messages matched, y-scaled to a "nice" rounded maximum with a
 * few gridlines. Each bar carries a native <title> for a zero-JS hover value; the full numbers are
 * also always reachable via StatsPage's "Show as table" toggle, so nothing is hover-only.
 */
export class DailyStatsChart implements m.ClassComponent<DailyStatsChartAttrs> {
  view({ attrs }: m.Vnode<DailyStatsChartAttrs>): m.Children {
    const days = attrs.days;
    if (days.length === 0) return null;

    const rawMax = Math.max(1, ...days.map((d) => Math.max(d.messagesProcessed, d.messagesMatched)));
    const step = niceStep(rawMax, 4);
    const niceMax = Math.ceil(rawMax / step) * step;
    const ticks: number[] = [];
    for (let v = 0; v <= niceMax; v += step) ticks.push(v);

    const groupWidth = PLOT_WIDTH / days.length;
    const groupPadding = groupWidth * 0.2;
    const barWidth = Math.max(1, (groupWidth - groupPadding * 2 - 2) / 2);
    const labelEvery = Math.max(1, Math.ceil(days.length / MAX_X_LABELS));

    const yFor = (value: number) => TOP_PADDING + PLOT_HEIGHT * (1 - value / niceMax);
    const barHeight = (value: number) => PLOT_HEIGHT * (value / niceMax);

    return m(".daily-stats-chart", [
      m(".chart-legend", [
        m(".legend-item", [m(".legend-swatch", { style: { backgroundColor: PROCESSED_COLOR } }), "Processed"]),
        m(".legend-item", [m(".legend-swatch", { style: { backgroundColor: MATCHED_COLOR } }), "Matched"]),
      ]),
      m("svg.daily-stats-chart-svg", {
        viewBox: `0 0 ${PLOT_WIDTH} ${VIEWBOX_HEIGHT}`,
        preserveAspectRatio: "none",
      }, [
        ...ticks.map((tick) => {
          const y = yFor(tick);
          return m("g.chart-gridline", { key: "grid-" + tick }, [
            m("line", { x1: 0, x2: PLOT_WIDTH, y1: y, y2: y }),
            m("text", { x: 2, y: y - 2 }, formatCount(tick)),
          ]);
        }),
        ...days.map((day, i) => {
          const groupX = i * groupWidth + groupPadding;
          const processedHeight = barHeight(day.messagesProcessed);
          const matchedHeight = barHeight(day.messagesMatched);
          const children: m.Children[] = [];
          if (processedHeight > 0) {
            children.push(m("path.bar.bar-processed", {
              key: "p",
              fill: PROCESSED_COLOR,
              d: topRoundedBarPath(groupX, PLOT_HEIGHT + TOP_PADDING - processedHeight, barWidth, processedHeight, BAR_RADIUS),
            }, m("title", day.date + " — " + day.messagesProcessed + " processed")));
          }
          if (matchedHeight > 0) {
            children.push(m("path.bar.bar-matched", {
              key: "m",
              fill: MATCHED_COLOR,
              d: topRoundedBarPath(groupX + barWidth + 2, PLOT_HEIGHT + TOP_PADDING - matchedHeight, barWidth, matchedHeight, BAR_RADIUS),
            }, m("title", day.date + " — " + day.messagesMatched + " matched")));
          }
          if (i % labelEvery === 0) {
            children.push(m("text.chart-x-label", {
              key: "l",
              x: groupX + barWidth + 1,
              y: PLOT_HEIGHT + TOP_PADDING + LABEL_HEIGHT - 6,
              "text-anchor": "middle",
            }, shortDate(day.date)));
          }
          return m("g.chart-day-group", { key: day.date }, children);
        }),
      ]),
    ]);
  }
}

/** Rounded top corners, square baseline — a bar "grows" from the bottom without floating. */
function topRoundedBarPath(x: number, y: number, width: number, height: number, radius: number): string {
  const r = Math.min(radius, width / 2, height);
  const bottom = y + height;
  return `M${x},${bottom} L${x},${y + r} Q${x},${y} ${x + r},${y} L${x + width - r},${y} `
    + `Q${x + width},${y} ${x + width},${y + r} L${x + width},${bottom} Z`;
}

/** Smallest "nice" step (1/2/5 x a power of ten) giving roughly targetTicks gridlines up to maxVal. */
function niceStep(maxVal: number, targetTicks: number): number {
  const roughStep = maxVal / targetTicks;
  const magnitude = Math.pow(10, Math.floor(Math.log10(roughStep || 1)));
  const residual = roughStep / magnitude;
  let niceResidual: number;
  if (residual > 5) niceResidual = 10;
  else if (residual > 2) niceResidual = 5;
  else if (residual > 1) niceResidual = 2;
  else niceResidual = 1;
  return niceResidual * magnitude;
}

function formatCount(value: number): string {
  return value >= 1000 ? (value / 1000).toFixed(value % 1000 === 0 ? 0 : 1) + "k" : String(value);
}

/** "2026-09-26" -> "Sep 26", short enough to sit under a thin bar group without colliding. */
function shortDate(isoDate: string): string {
  const date = new Date(isoDate + "T00:00:00Z");
  return date.toLocaleDateString(undefined, { month: "short", day: "numeric", timeZone: "UTC" });
}
