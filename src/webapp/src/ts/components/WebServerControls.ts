import m from "mithril";
import { ApiEndpoints } from "../auto/ApiEndpoints";
import { Dialogs } from "../utils/Dialogs";
import { Notification, Notifications, NotificationsClass, NotificationsType } from "../utils/Notifications";
import { WebServerMovedDialog } from "./WebServerMovedDialog";

/**
 * The web server's port/address (and enabled state) as read-only values, each with a button that
 * applies its change immediately — shared by GeneralSettingsPage and SetupWizardPage.
 */
export class WebServerControls {
  constructor(private enabled: boolean, private port: number, private address: string) {
  }

  /** @param withStatus false in the setup wizard: disabling the web server there would end the wizard itself. */
  render(withStatus: boolean): m.Children {
    return m(".config-grid", [
      withStatus ? actionRow("Status", this.enabled ? "Enabled" : "Disabled",
        this.enabled ? m("button.danger-button", { onclick: () => this.disable() }, "Disable") : null) : null,
      actionRow("Port", String(this.port),
        m("button.secondary", { onclick: () => this.changePort() }, "Change")),
      actionRow("Address", this.address || m("span.config-row-hint", "(all interfaces)"),
        m("button.secondary", { onclick: () => this.changeAddress() }, "Change")),
    ]);
  }

  private changePort() {
    Dialogs.prompt("New web server port:", String(this.port), (value) => {
      const port = Number(value.trim());
      if (!Number.isInteger(port) || port < 1 || port > 65535) return new Error("The port must be a number between 1 and 65535.");
      if (port === this.port) return new Error("That's already the current port.");
      return port;
    }, (port) => {
      const direct = this.servedDirectly();
      ApiEndpoints.ChangeWebServerPort.call({ port })
        .then(() => {
          this.port = port;
          Dialogs.add(new WebServerMovedDialog("port " + port, direct ? urlFor(this.address, port) : null));
          m.redraw();
        })
        .catch((err: Error) => notifyError(err.message));
    });
  }

  private changeAddress() {
    Dialogs.prompt("New web server address (leave empty for all interfaces):", this.address, (value) => {
      const address = value.trim();
      if (/\s/.test(address)) return new Error("The address must not contain spaces.");
      if (address === this.address) return new Error("That's already the current address.");
      return address;
    }, (address) => {
      // Deferred: the prompt dismisses the top dialog right after this callback returns.
      window.setTimeout(() => {
        if (isLoopback(address) && !isLoopback(window.location.hostname)) {
          Dialogs.confirm("This browser reaches MOM through " + window.location.hostname + ". Once MOM only listens on "
            + address + ", only a browser running on the MOM machine itself will reach it.", "Change anyway", "Cancel",
            () => this.applyAddress(address));
        } else {
          this.applyAddress(address);
        }
      });
    });
  }

  private applyAddress(address: string) {
    const direct = this.servedDirectly();
    const port = this.port;
    ApiEndpoints.ChangeWebServerAddress.call({ address })
      .then((output) => {
        this.address = address;
        const newUrl = direct ? urlFor(address, port) : null;
        let dialog: WebServerMovedDialog | undefined;
        if (newUrl && new URL(newUrl).hostname === window.location.hostname) {
          Notifications.addNotification(new Notification(NotificationsClass.WEB_SERVER_CHANGE, NotificationsType.SUCCESS,
            "The web server now listens on " + describeListen(address, port) + ".", 5));
        } else {
          dialog = new WebServerMovedDialog(describeListen(address, port), newUrl);
          Dialogs.add(dialog);
        }
        if (!output.appliedImmediately) window.setTimeout(() => this.checkAddressSwitch(address, dialog), ADDRESS_SWITCH_CHECK_MS);
        m.redraw();
      })
      .catch((err: Error) => notifyError(err.message));
  }

  /**
   * After a delayed switch-over (see WebServerService#changeAddress): if this page's address still
   * answers, either the new address includes it or the switch failed and MOM went back to it. A
   * plain fetch first, so a successful move (this address gone) doesn't raise a "server
   * unreachable" notification.
   */
  private checkAddressSwitch(address: string, dialog: WebServerMovedDialog | undefined) {
    fetch(window.location.origin + "/", { cache: "no-store" })
      .then(() => ApiEndpoints.GeneralSettings.call({}))
      .then((output) => {
        if ((output.webServerAddress || "") === address) return;
        this.address = output.webServerAddress || "";
        if (dialog && Dialogs.getCurrent() === dialog) Dialogs.dismiss();
        notifyError(output.webServerAddressChangeError || "The address change failed: see MOM's log.");
      })
      .catch(() => undefined);
  }

  /** False when the page comes through something else than MOM's own port (e.g. a reverse proxy). */
  private servedDirectly(): boolean {
    const browserPort = window.location.port ? Number(window.location.port) : (window.location.protocol === "https:" ? 443 : 80);
    return browserPort === this.port;
  }

  private disable() {
    Dialogs.confirm("Disable the web server? This page will stop responding right away. To bring it back, set "
      + "webServer.enabled to true in config.json and restart MOM.", "Disable", "Cancel", () => {
      ApiEndpoints.DisableWebServer.call({})
        .then(() => {
          this.enabled = false;
          Notifications.addNotification(new Notification(NotificationsClass.WEB_SERVER_CHANGE, NotificationsType.SUCCESS,
            "The web server is disabled and stops in a moment. To bring it back, set webServer.enabled to true in config.json and restart MOM.", 60));
          m.redraw();
        })
        .catch((err: Error) => notifyError(err.message));
    });
  }
}

// Leaves time for WebServerService's delayed address switch-over (1s) to happen.
const ADDRESS_SWITCH_CHECK_MS = 3_000;

function notifyError(message: string) {
  Notifications.addNotification(new Notification(NotificationsClass.WEB_SERVER_CHANGE, NotificationsType.ERROR, message, 8));
  m.redraw();
}

function isLoopback(host: string): boolean {
  return host === "localhost" || host.startsWith("127.") || host === "::1" || host === "[::1]";
}

/** Same format as WebServerService's logs, e.g. "127.0.0.1:8080", "*:8080 (all interfaces)". */
function describeListen(address: string, port: number): string {
  if (!address) return "*:" + port + " (all interfaces)";
  return (address.includes(":") ? "[" + address + "]" : address) + ":" + port;
}

/** Where this browser can reach the web server once it listens on address:port. */
function urlFor(address: string, port: number): string {
  const current = window.location.hostname;
  let host: string;
  if (!address || address === "0.0.0.0" || address === "::") host = current;
  else if (isLoopback(address)) host = isLoopback(current) ? current : "localhost";
  else host = address.includes(":") ? "[" + address + "]" : address;
  return window.location.protocol + "//" + host + ":" + port + "/";
}

function actionRow(label: string, value: m.Children, action: m.Children): m.Children {
  return m(".config-row", [m(".config-row-label", label), m(".config-row-value.with-action", [m("span", value), action])]);
}

