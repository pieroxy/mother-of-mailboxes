import m from "mithril";

interface CycleProgressBarAttrs {
  lastCycleCompletedTimestamp: string;
  nextScheduledCycleTimestamp: string;
}

/**
 * Visualizes an account's cycle timing (see AccountsApi): a bar filling up from the last
 * completed cycle towards the next scheduled one, with "Xs ago" / "in Ys" labels. Recomputed
 * from the account's own timestamps against Date.now() on every render — HomePage redraws this
 * every second so the numbers actually tick, without re-fetching account data that often.
 */
export class CycleProgressBar implements m.ClassComponent<CycleProgressBarAttrs> {
  view({ attrs }: m.Vnode<CycleProgressBarAttrs>): m.Children {
    const last = attrs.lastCycleCompletedTimestamp ? Date.parse(attrs.lastCycleCompletedTimestamp) : NaN;
    const next = attrs.nextScheduledCycleTimestamp ? Date.parse(attrs.nextScheduledCycleTimestamp) : NaN;
    if (isNaN(last) || isNaN(next)) {
      return m(".cycle-progress.cycle-progress-pending", "Waiting for the first cycle…");
    }

    const now = Date.now();
    const secondsSinceLastRun = Math.max(0, Math.round((now - last) / 1000));
    const secondsUntilNextRun = Math.round((next - now) / 1000);
    const percent = next > last ? Math.min(100, Math.max(0, ((now - last) / (next - last)) * 100)) : 100;

    return m(".cycle-progress", [
      m(".cycle-progress-bar", m(".cycle-progress-bar-fill", { style: { width: percent + "%" } })),
      m(".cycle-progress-labels", [
        m("span.cycle-progress-last", secondsSinceLastRun + "s ago"),
        m("span.cycle-progress-next", secondsUntilNextRun > 0 ? "next in " + secondsUntilNextRun + "s" : "next any moment"),
      ]),
    ]);
  }
}
