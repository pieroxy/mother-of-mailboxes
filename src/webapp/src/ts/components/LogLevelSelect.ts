import m from "mithril";

interface LogLevelSelectAttrs {
  /** Absent or empty: the default (INFO). */
  value: string | undefined;
  /** Receives undefined for the default, so it isn't written out to config.json. */
  onChange: (logLevel: string | undefined) => void;
}

const LEVELS = ["DEBUG", "INFO", "WARNING", "ERROR"];

/** A matcher or action node's own log level (see LogLevels on the backend). */
export class LogLevelSelect implements m.ClassComponent<LogLevelSelectAttrs> {
  view({ attrs }: m.Vnode<LogLevelSelectAttrs>): m.Children {
    const value = (attrs.value || "").toUpperCase();
    return m("select.log-level-select", {
      value: LEVELS.includes(value) ? value : "",
      title: "Log level of this node only. DEBUG logs its full evaluation trace (e.g. the DNS records an SPF check fetched).",
      onchange: (e: Event) => attrs.onChange((e.target as HTMLSelectElement).value || undefined),
    }, [
      m("option", { value: "" }, "Log: default"),
      LEVELS.map((level) => m("option", { value: level }, "Log: " + level)),
    ]);
  }
}
