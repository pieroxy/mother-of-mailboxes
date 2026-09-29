import m from 'mithril';

export class StatsIcon implements m.ClassComponent {
  public view() {
    return m("svg.icon", {
      viewBox: "0 0 24 24",
      fill: "none",
      xmlns: "http://www.w3.org/2000/svg",
    }, [
      m("path.secondary", {
        d: "M13.5 3.5H20.5V20.5H13.5V3.5Z",
      }),
      m("path.primary", {
        d: "M3.5 9.17H10.5V20.5H3.5V9.17Z",
      }),
      m("path.primary", {
        d: "M13.5 3.5H20.5V20.5H13.5V3.5Z",
      }),
    ])
  }
}
