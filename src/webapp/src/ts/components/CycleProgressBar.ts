import m from "mithril";
import { formatDuration } from "../utils/format";

interface CycleProgressBarAttrs {
  /** ISO-8601 timestamp the last cycle/refresh completed, or "" if none has happened yet. */
  lastTimestamp: string;
  /** ISO-8601 timestamp the next one is expected, or "" if unknown (see lastTimestamp). */
  nextTimestamp: string;
}

/**
 * Visualizes any "last completed / next expected" timing — an account's cycle (see AccountsApi)
 * or a reputation list's refresh (see ReputationListsApi, whose "next" isn't server-computed:
 * the caller derives it from lastRefreshTimestamp + refreshHours, client-side, same as status).
 * A bar filling up from last towards next, with "Xs ago" / "in Ys" labels. Recomputed against
 * Date.now() on every render — HomePage redraws this every second so the numbers actually tick,
 * without re-fetching data that often.
 */
export class CycleProgressBar implements m.ClassComponent<CycleProgressBarAttrs> {
  view({ attrs }: m.Vnode<CycleProgressBarAttrs>): m.Children {
    const last = attrs.lastTimestamp ? Date.parse(attrs.lastTimestamp) : NaN;
    const next = attrs.nextTimestamp ? Date.parse(attrs.nextTimestamp) : NaN;
    if (isNaN(last) || isNaN(next)) {
      return m(".cycle-progress.cycle-progress-pending", "Nothing recorded yet…");
    }

    const now = Date.now();
    const secondsSinceLastRun = Math.max(0, (now - last) / 1000);
    const secondsUntilNextRun = (next - now) / 1000;
    const percent = next > last ? Math.min(100, Math.max(0, ((now - last) / (next - last)) * 100)) : 100;

    return m(".cycle-progress", [
      m(".cycle-progress-bar", m(".cycle-progress-bar-fill", { style: { width: percent + "%" } })),
      m(".cycle-progress-labels", [
        m("span.cycle-progress-last", formatDuration(secondsSinceLastRun) + " ago"),
        m("span.cycle-progress-next", secondsUntilNextRun > 0 ? "next in " + formatDuration(secondsUntilNextRun) : "next any moment"),
      ]),
    ]);
  }
}
