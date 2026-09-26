import m from 'mithril';

export class StatsIcon implements m.ClassComponent {
  public view() {
    return m("svg.icon", {
      viewBox: "0 -960 960 960",
      fill: "none",
      xmlns: "http://www.w3.org/2000/svg",
    }, [
      m("path.primaryfill", {
        d: "M160-200h160v-320H160v320Zm240 0h160v-560H400v560Zm240 0h160v-240H640v240ZM80-120v-480h240v-240h320v320h240v400H80Z",
      }),
    ])
  }
}
