import m from "mithril";
import { DialogRender, Dialogs } from "../utils/Dialogs";
import { Button } from "./atoms/forms/Button";

const POLL_INTERVAL_MS = 1_000;
const GIVE_UP_AFTER_MS = 30_000;

/**
 * Shown once the web server listens on a new port (the old one closes a few seconds later): waits
 * for the new address to answer, then goes there. When this page wasn't loaded straight from the
 * old port (e.g. through a reverse proxy), the new address can't be guessed, so it only explains.
 */
export class WebServerMovedDialog extends DialogRender {
  key = "web-server-moved";
  title = undefined;
  private readonly direct: boolean;
  private readonly newUrl: string;
  private status: "waiting" | "reachable" | "unreachable" = "waiting";
  private timer: number | undefined;
  private closed = false;

  constructor(private readonly oldPort: number, private readonly newPort: number) {
    super();
    const browserPort = window.location.port ? Number(window.location.port) : (window.location.protocol === "https:" ? 443 : 80);
    this.direct = browserPort === oldPort;
    this.newUrl = window.location.protocol + "//" + window.location.hostname + ":" + newPort + "/";
    if (this.direct) this.poll(Date.now());
  }

  protected view(): m.Vnode<any, any> {
    if (!this.direct) {
      return m("", [
        m("p", "The web server now listens on port " + this.newPort + " instead of " + this.oldPort + "."),
        m("p", "This page is reached through another address (a reverse proxy, for instance): update it to forward to port "
          + this.newPort + ", or this page will stop working in a few seconds."),
        m(".buttonpanel", m(Button, { action: () => this.close() }, "OK")),
      ]);
    }
    return m("", [
      m("p", "The web server now listens on port " + this.newPort + ". This address stops working in a few seconds."),
      m("p", "You'll have to log in again at the new address."),
      m("p", this.statusText()),
      m(".buttonpanel", [
        m(Button, { action: () => (window.location.href = this.newUrl) }, "Open " + this.newUrl),
        m(Button, { secondary: true, action: () => this.close() }, "Stay here"),
      ]),
    ]);
  }

  private statusText(): string {
    switch (this.status) {
      case "waiting": return "Waiting for " + this.newUrl + " to answer…";
      case "reachable": return this.newUrl + " answers, opening it…";
      default: return "No answer from " + this.newUrl + " yet: a firewall may be blocking port " + this.newPort + ".";
    }
  }

  /** A no-cors fetch can't read the response, but resolves on any answer and rejects on a network error. */
  private poll(startedAt: number) {
    fetch(this.newUrl, { mode: "no-cors", cache: "no-store" })
      .then(() => {
        if (this.closed) return;
        this.status = "reachable";
        m.redraw();
        window.location.href = this.newUrl;
      })
      .catch(() => {
        if (this.closed) return;
        if (Date.now() - startedAt > GIVE_UP_AFTER_MS) this.status = "unreachable";
        this.timer = window.setTimeout(() => this.poll(startedAt), POLL_INTERVAL_MS);
        m.redraw();
      });
  }

  private close() {
    this.closed = true;
    if (this.timer !== undefined) window.clearTimeout(this.timer);
    this.timer = undefined;
    Dialogs.dismiss();
  }
}
