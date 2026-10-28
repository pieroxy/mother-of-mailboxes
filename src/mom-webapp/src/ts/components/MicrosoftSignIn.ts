import m from "mithril";
import { ApiEndpoints } from "../auto/ApiEndpoints";
import { SignInState, StartMicrosoftSignInApiOutput } from "../auto/pieroxy-mom";

export const MICROSOFT_IMAP_HOST = "outlook.office365.com";
export const MICROSOFT_SMTP_HOST = "smtp-mail.outlook.com";
const APP_REGISTRATIONS_URL = "https://entra.microsoft.com/#view/Microsoft_AAD_RegisteredApps/ApplicationsListBlade";

interface MicrosoftSignInAttrs {
  /** The user's own app registration; empty: MOM's. */
  clientId: string;
  onClientIdChange: (clientId: string) => void;
  onSignedIn: (signInId: string, username: string | null) => void;
}

/**
 * Signs in with the device code flow: StartMicrosoftSignInApi gives a code to type at Microsoft,
 * MicrosoftSignInStatusApi tells when it's done. Uses MOM's app registration, or the user's own,
 * whose registration steps it lists. Shared by the account creation wizard and
 * AccountImapSettingsEditPage.
 */
export class MicrosoftSignIn implements m.ClassComponent<MicrosoftSignInAttrs> {
  private starting = false;
  private code: StartMicrosoftSignInApiOutput | undefined;
  private signedInAs: string | null | undefined;
  private error: string | undefined;
  private pollTimer: number | undefined;
  private ownRegistrationShown = false;

  oninit({ attrs }: m.Vnode<MicrosoftSignInAttrs>) {
    this.ownRegistrationShown = attrs.clientId !== "";
  }

  onremove() {
    this.stopPolling();
  }

  view({ attrs }: m.Vnode<MicrosoftSignInAttrs>): m.Children {
    return m(".microsoft-sign-in", [
      m(".microsoft-sign-in-action", [
        m("button.secondary", { onclick: () => this.start(attrs), disabled: this.starting || this.code !== undefined },
          this.signedInAs !== undefined ? "Sign in again" : "Sign in with Microsoft"),
        this.code ? m("button.cancel-button", { onclick: () => this.cancel() }, "Cancel") : null,
        this.signedInAs !== undefined && !this.code
          ? m(".test-connection-result.ok", "Signed in" + (this.signedInAs ? " as " + this.signedInAs : "") + ".") : null,
        this.error ? m(".test-connection-result.error", this.error) : null,
      ]),
      this.code ? m(".microsoft-sign-in-code", [
        m("span", ["Open ", m("a", { href: this.code.verificationUri, target: "_blank", rel: "noopener" }, this.code.verificationUri),
          " and enter this code:"]),
        m(".user-code", this.code.userCode),
        m("span", "Then sign in with this mailbox's account, and accept the permissions MOM asks for: reading and sending its mail. "
          + "This page carries on by itself once you're done."),
      ]) : null,
      m("details.microsoft-sign-in-steps", {
        open: this.ownRegistrationShown,
        ontoggle: (e: Event) => (this.ownRegistrationShown = (e.target as HTMLDetailsElement).open),
      }, [
        m("summary", "Use your own app registration (optional)"),
        m("p", "Only needed if MOM's isn't allowed, e.g. by a work account's administrator. To register MOM yourself:"),
        m("ol", [
          m("li", ["Open ", m("a", { href: APP_REGISTRATIONS_URL, target: "_blank", rel: "noopener" }, "App registrations"),
            " in the Microsoft Entra admin center. Your account must belong to a directory: a free Azure account creates one."]),
          m("li", ["Click ", m("b", "New registration"), ". Name it as you like, e.g. MOM. Under ", m("b", "Supported account types"),
            ", pick the one including ", m("b", "personal Microsoft accounts"), " if the mailbox is one. Leave ", m("b", "Redirect URI"),
            " empty, and click ", m("b", "Register"), "."]),
          m("li", ["In the app's ", m("b", "Authentication"), " page, set ", m("b", "Allow public client flows"), " to ", m("b", "Yes"),
            ", and save."]),
          m("li", ["On the app's ", m("b", "Overview"), " page, copy the ", m("b", "Application (client) ID"), " here:"]),
        ]),
        m(".edit-field", [
          m("label", "Application (client) ID"),
          m("input", {
            type: "text", value: attrs.clientId, placeholder: "Empty: MOM's", autocomplete: "off",
            oninput: (e: Event) => attrs.onClientIdChange((e.target as HTMLInputElement).value),
          }),
        ]),
      ]),
    ]);
  }

  private start(attrs: MicrosoftSignInAttrs) {
    this.starting = true;
    this.error = undefined;
    ApiEndpoints.StartMicrosoftSignIn.call({ clientId: attrs.clientId.trim() })
      .then((code) => {
        this.starting = false;
        this.code = code;
        this.schedulePoll(attrs, code);
        m.redraw();
      })
      .catch((err: Error) => {
        this.starting = false;
        this.error = err.message;
        m.redraw();
      });
  }

  private schedulePoll(attrs: MicrosoftSignInAttrs, code: StartMicrosoftSignInApiOutput) {
    this.pollTimer = window.setTimeout(() => this.poll(attrs, code), Math.max(code.intervalSeconds, 2) * 1000);
  }

  private poll(attrs: MicrosoftSignInAttrs, code: StartMicrosoftSignInApiOutput) {
    ApiEndpoints.MicrosoftSignInStatus.call({ signInId: code.signInId })
      .then((status) => {
        if (this.code !== code) return;
        if (status.state === SignInState.PENDING) {
          this.schedulePoll(attrs, code);
          return;
        }
        this.code = undefined;
        if (status.state === SignInState.SIGNED_IN) {
          this.signedInAs = status.username ?? null;
          attrs.onSignedIn(code.signInId, status.username ?? null);
        } else {
          this.error = status.message;
        }
        m.redraw();
      })
      .catch((err: Error) => {
        if (this.code !== code) return;
        this.code = undefined;
        this.error = err.message;
        m.redraw();
      });
  }

  private cancel() {
    this.stopPolling();
    this.code = undefined;
  }

  private stopPolling() {
    if (this.pollTimer !== undefined) window.clearTimeout(this.pollTimer);
    this.pollTimer = undefined;
  }
}
