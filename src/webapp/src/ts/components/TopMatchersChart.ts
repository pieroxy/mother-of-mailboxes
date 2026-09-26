import m from "mithril";
import { MatcherCountDto } from "../auto/pieroxy-mom";

interface TopMatchersChartAttrs {
  /** Every matcher that fired at least once, whether or not it ended processing for a message. */
  allMatchers: MatcherCountDto[];
  /** Of those, only the ones that actually ended processing for a message at least once. */
  blockingMatchers: MatcherCountDto[];
}

type Mode = "all" | "blocking";

/**
 * Ranked horizontal bar list of which matchers fired over the selected range, with a switch
 * between every matcher that fired (a {@code keepProcessing} rule fires without ever blocking
 * anything — see MailFilterRuleConfiguration) and only the ones that actually ended processing
 * for a message. A single measure (fire count) across named categories, so a single hue (the
 * app's own teal accent, magnitude not identity) rather than a categorical palette.
 */
export class TopMatchersChart implements m.ClassComponent<TopMatchersChartAttrs> {
  private mode: Mode = "all";

  view({ attrs }: m.Vnode<TopMatchersChartAttrs>): m.Children {
    const matchers = this.mode === "all" ? attrs.allMatchers : attrs.blockingMatchers;
    const maxCount = matchers.length > 0 ? Math.max(...matchers.map((entry) => entry.count)) : 0;

    return m(".top-matchers-container", [
      m(".matcher-mode-switch", [
        m("button.matcher-mode-option" + (this.mode === "all" ? ".active" : ""),
          { onclick: () => (this.mode = "all") }, "All matches"),
        m("button.matcher-mode-option" + (this.mode === "blocking" ? ".active" : ""),
          { onclick: () => (this.mode = "blocking") }, "Ended processing"),
      ]),
      matchers.length === 0
        ? m(".top-matchers.top-matchers-empty", this.mode === "all"
            ? "No matcher fired in this range."
            : "No matcher ended processing in this range.")
        : m(".top-matchers", matchers.map((entry) => m(".matcher-row", { key: entry.matcher }, [
            m(".matcher-label", { title: entry.matcher }, entry.matcher),
            m(".matcher-bar-track", m(".matcher-bar-fill", { style: { width: (entry.count / maxCount) * 100 + "%" } })),
            m(".matcher-count", entry.count),
          ]))),
    ]);
  }
}
