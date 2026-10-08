import m from "mithril";
import { DialogRender, Dialogs } from "../utils/Dialogs";
import { Button } from "./atoms/forms/Button";

/**
 * Shown once the web server listens on a new port (the old one closes a few seconds later). When
 * this page wasn't loaded straight from the old port (e.g. through a reverse proxy), the new
 * address can't be guessed, so it only explains.
 */
export class WebServerMovedDialog extends DialogRender {
  key = "web-server-moved";
  title = undefined;
  private readonly direct: boolean;
  private readonly newUrl: string;

  constructor(private readonly oldPort: number, private readonly newPort: number) {
    super();
    const browserPort = window.location.port ? Number(window.location.port) : (window.location.protocol === "https:" ? 443 : 80);
    this.direct = browserPort === oldPort;
    this.newUrl = window.location.protocol + "//" + window.location.hostname + ":" + newPort + "/";
  }

  protected view(): m.Vnode<any, any> {
    if (!this.direct) {
      return m("", [
        m("p", "The web server now listens on port " + this.newPort + " instead of " + this.oldPort + "."),
        m("p", "This page is reached through another address (a reverse proxy, for instance): update it to forward to port "
          + this.newPort + ", or this page will stop working in a few seconds."),
        m(".buttonpanel", m(Button, { action: () => Dialogs.dismiss() }, "OK")),
      ]);
    }
    return m("", [
      m("p", "The web server now listens on port " + this.newPort + "."),
      m(".buttonpanel", [
        m(Button, { action: () => (window.location.href = this.newUrl) }, "Go there"),
        m(Button, { secondary: true, action: () => Dialogs.dismiss() }, "Close"),
      ]),
    ]);
  }
}
