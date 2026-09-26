import m from "mithril";
import { MatcherCountDto } from "../auto/pieroxy-mom";

interface TopMatchersChartAttrs {
  matchers: MatcherCountDto[];
}

/**
 * Ranked horizontal bar list of which matchers actually fired over the selected range — a single
 * measure (fire count) across named categories, so a single hue (the app's own teal accent, magnitude
 * not identity) rather than a categorical palette. Already sorted/capped by the API (top N).
 */
export class TopMatchersChart implements m.ClassComponent<TopMatchersChartAttrs> {
  view({ attrs }: m.Vnode<TopMatchersChartAttrs>): m.Children {
    const matchers = attrs.matchers;
    if (matchers.length === 0) {
      return m(".top-matchers.top-matchers-empty", "No matcher fired in this range.");
    }

    const maxCount = Math.max(...matchers.map((m) => m.count));

    return m(".top-matchers", matchers.map((entry) => m(".matcher-row", { key: entry.matcher }, [
      m(".matcher-label", { title: entry.matcher }, entry.matcher),
      m(".matcher-bar-track", m(".matcher-bar-fill", { style: { width: (entry.count / maxCount) * 100 + "%" } })),
      m(".matcher-count", entry.count),
    ])));
  }
}
