import m from 'mithril';

export class StatusInProgressIcon implements m.ClassComponent {
  public view() {
    return m("svg.icon.inprogress", {
      viewBox: "0 0 24 24",
      fill: "none",
      xmlns: "http://www.w3.org/2000/svg",
    }, [
      m("circle.primaryfill.dot", { cx: 7.5, cy: 12, r: 1.25 }),
      m("circle.primaryfill.dot", { cx: 12, cy: 12, r: 1.25 }),
      m("circle.primaryfill.dot", { cx: 16.5, cy: 12, r: 1.25 }),
      m("path.secondary", {
        d: "M21 12C21 16.9706 16.9706 21 12 21C7.02944 21 3 16.9706 3 12C3 7.02944 7.02944 3 12 3C16.9706 3 21 7.02944 21 12Z",
      }),
      m("path.primary", {
        d: "M21 12C21 16.9706 16.9706 21 12 21C7.02944 21 3 16.9706 3 12C3 7.02944 7.02944 3 12 3C16.9706 3 21 7.02944 21 12Z",
      }),
    ])
  }
}
