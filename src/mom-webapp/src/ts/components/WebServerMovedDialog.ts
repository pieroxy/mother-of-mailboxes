import m from "mithril";
import { DialogRender, Dialogs } from "../utils/Dialogs";
import { Button } from "./atoms/forms/Button";

/**
 * Shown once the web server listens somewhere else (the old address/port closes a few seconds
 * later). newUrl is null when this page wasn't loaded straight from the web server (e.g. through a
 * reverse proxy): the new address can't be guessed then, so it only explains.
 */
export class WebServerMovedDialog extends DialogRender {
  key = "web-server-moved";
  title = undefined;

  /** @param listening what the web server now listens on, e.g. "port 8081" or "192.168.1.10:8080". */
  constructor(private readonly listening: string, private readonly newUrl: string | null) {
    super();
  }

  protected view(): m.Vnode<any, any> {
    if (!this.newUrl) {
      return m("", [
        m("p", "The web server now listens on " + this.listening + "."),
        m("p", "This page is reached through another address (a reverse proxy, for instance): update it to forward to "
          + this.listening + ", or this page will stop working in a few seconds."),
        m(".buttonpanel", m(Button, { action: () => Dialogs.dismiss() }, "OK")),
      ]);
    }
    const newUrl = this.newUrl;
    return m("", [
      m("p", "The web server now listens on " + this.listening + "."),
      m(".buttonpanel", [
        m(Button, { action: () => (window.location.href = newUrl) }, "Go there"),
        m(Button, { secondary: true, action: () => Dialogs.dismiss() }, "Close"),
      ]),
    ]);
  }
}
