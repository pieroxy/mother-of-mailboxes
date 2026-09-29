import m from 'mithril';

export class StatusPausedIcon implements m.ClassComponent {
  public view() {
    return m("svg.icon.paused", {
      viewBox:"0 0 24 24",
      fill:"none",
      xmlns:"http://www.w3.org/2000/svg"
    }, [
      m("path.primaryfill", {
        d:"M8.25 6H9.75V18H8.25V6ZM14.25 6H15.75V18H14.25V6Z",
      }),
      m("path.secondary", {
        d:"M21 12C21 16.9706 16.9706 21 12 21C7.02944 21 3 16.9706 3 12C3 7.02944 7.02944 3 12 3C16.9706 3 21 7.02944 21 12Z",
      }),
    ])
  }
}
