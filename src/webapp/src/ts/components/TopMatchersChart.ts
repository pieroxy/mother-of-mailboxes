import m from "mithril";
import { MatcherCountDto } from "../auto/pieroxy-mom";

interface TopMatchersChartAttrs {
  /** Every matcher that fired at least once, whether or not it ended processing for a message. */
  allMatchers: MatcherCountDto[];
  /** Of those, only the ones that actually ended processing for a message at least once. */
  blockingMatchers: MatcherCountDto[];
  /** Same population as {@code allMatchers}, merged by matcher type (e.g. every FROM_ADDRESS_EQUALS sender collapsed into one entry) — see StatsApi#byType. */
  allMatchersByType: MatcherCountDto[];
  /** Same population as {@code blockingMatchers}, merged by matcher type. */
  blockingMatchersByType: MatcherCountDto[];
}

type Mode = "all" | "blocking";
type Grouping = "detailed" | "byType";

/**
 * Ranked horizontal bar list of which matchers fired over the selected range, with two
 * independent switches: every matcher that fired (a {@code keepProcessing} rule fires without
 * ever blocking anything — see MailFilterRuleConfiguration) vs. only the ones that actually ended
 * processing for a message, and — since a matcher like FROM_ADDRESS_EQUALS fragments into one
 * entry per sender address while FROM_DOMAIN_EQUALS naturally doesn't, making the former look
 * underrepresented purely from its own detail — the detailed name vs. merged by matcher type. A
 * single measure (fire count) across named categories, so a single hue (the app's own teal
 * accent, magnitude not identity) rather than a categorical palette.
 */
export class TopMatchersChart implements m.ClassComponent<TopMatchersChartAttrs> {
  private mode: Mode = "all";
  private grouping: Grouping = "detailed";

  view({ attrs }: m.Vnode<TopMatchersChartAttrs>): m.Children {
    const byMode = this.mode === "all"
      ? (this.grouping === "detailed" ? attrs.allMatchers : attrs.allMatchersByType)
      : (this.grouping === "detailed" ? attrs.blockingMatchers : attrs.blockingMatchersByType);
    const maxCount = byMode.length > 0 ? Math.max(...byMode.map((entry) => entry.count)) : 0;

    return m(".top-matchers-container", [
      m(".matcher-mode-switch", [
        m("button.matcher-mode-option" + (this.mode === "all" ? ".active" : ""),
          { onclick: () => (this.mode = "all") }, "All matches"),
        m("button.matcher-mode-option" + (this.mode === "blocking" ? ".active" : ""),
          { onclick: () => (this.mode = "blocking") }, "Ended processing"),
      ]),
      m(".matcher-mode-switch", [
        m("button.matcher-mode-option" + (this.grouping === "detailed" ? ".active" : ""),
          { onclick: () => (this.grouping = "detailed") }, "Detailed"),
        m("button.matcher-mode-option" + (this.grouping === "byType" ? ".active" : ""),
          { onclick: () => (this.grouping = "byType") }, "By matcher type"),
      ]),
      byMode.length === 0
        ? m(".top-matchers.top-matchers-empty", this.mode === "all"
            ? "No matcher fired in this range."
            : "No matcher ended processing in this range.")
        : m(".top-matchers", byMode.map((entry) => m(".matcher-row", { key: entry.matcher }, [
            m(".matcher-label", { title: entry.matcher }, entry.matcher),
            m(".matcher-bar-track", m(".matcher-bar-fill", { style: { width: (entry.count / maxCount) * 100 + "%" } })),
            m(".matcher-count", entry.count),
          ]))),
    ]);
  }
}
