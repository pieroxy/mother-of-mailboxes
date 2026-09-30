import m from "mithril";
import { AbstractPage } from "./AbstractPage";
import { Routing } from "../../utils/navigation/Routing";
import { TestImapConnectionButton } from "../TestImapConnectionButton";
import { AccountEditSession, accountEditSessions, isConfigFieldChanged } from "../../utils/AccountEditSession";

interface AccountImapSettingsEditPageAttrs {
  accountName: string;
}

/**
 * Edits everything needed to connect to the mailbox — host, port, username, password — grouped
 * together since they're only ever useful as a whole (see AccountSettingsPage's "IMAP settings"
 * card). The password field always starts empty — the server never sends the real one down (see
 * AccountConfigApi's CredentialsInfoDto) — and stays that way unless the user types a new one:
 * leaving it blank means "keep whatever password is already staged" (or the current one, if none
 * is), never "erase it"; same rule TestImapConnectionButton's password fallback uses. Nothing is
 * sent to the backend by "OK" itself: it just writes the edited fields into the account's
 * AccountEditSession and returns to the settings page, where "Save Changes" persists the whole
 * batch in one call.
 */
export class AccountImapSettingsEditPage extends AbstractPage<AccountImapSettingsEditPageAttrs> {
  private accountName = "";
  private session: AccountEditSession | undefined;
  private host = "";
  private port = 0;
  private username = "";
  private password = "";
  private loading = true;
  private error: string | undefined;

  getPageTitle(): string {
    return "MOM - Edit IMAP settings";
  }

  oninit({ attrs }: m.Vnode<AccountImapSettingsEditPageAttrs>) {
    this.accountName = attrs.accountName;
    this.load();
  }

  render(): m.Children {
    return m("page.accountimapsettingseditpage", [
      m(".page-header", [
        m("a.page-back", { onclick: () => this.cancel() }, "‹ Back to settings"),
        m("h1.page-title", "Edit IMAP settings — " + this.accountName),
      ]),
      this.error ? m(".settings-error.errorMessage", this.error) : null,
      this.loading || !this.session ? m(".page-loading", "Loading…") : this.renderForm(this.session),
    ]);
  }

  private renderForm(session: AccountEditSession): m.Children {
    const baseline = session.baselineConfig;
    return m(".page-card.edit-form", [
      this.field("Host", this.originalHint(session, "host", baseline.host), m("input", {
        type: "text", value: this.host,
        oninput: (e: Event) => (this.host = (e.target as HTMLInputElement).value),
      })),
      this.field("Port", this.originalHint(session, "port", String(baseline.port)), m("input", {
        type: "number", value: this.port,
        oninput: (e: Event) => (this.port = Number((e.target as HTMLInputElement).value)),
      })),
      m(".edit-field", [
        m("label", "Username"),
        m("input", {
          type: "text", value: this.username,
          oninput: (e: Event) => (this.username = (e.target as HTMLInputElement).value),
        }),
        session.workingUsername !== session.baselineUsername
          ? m("span.field-original", "originally: " + session.baselineUsername) : null,
      ]),
      m(".edit-field", [
        m("label", "Password"),
        m("input", {
          type: "password", value: this.password, placeholder: "Leave blank to keep the current password",
          oninput: (e: Event) => (this.password = (e.target as HTMLInputElement).value),
        }),
        m("span.field-hint", "The current password is never shown here — leave this blank to keep it unchanged."),
        session.workingPassword !== "" && this.password === ""
          ? m("span.field-original", "A new password is already staged for this account.") : null,
      ]),
      m(TestImapConnectionButton, {
        accountName: this.accountName, host: this.host, port: this.port, username: this.username, password: this.password,
      }),
      m(".edit-actions", [
        m("button.ok-button", { onclick: () => this.apply() }, "OK"),
        m("button.cancel-button", { onclick: () => this.cancel() }, "Cancel"),
      ]),
    ]);
  }

  private originalHint(session: AccountEditSession, field: keyof AccountEditSession["baselineConfig"], baselineText: string): string | null {
    return isConfigFieldChanged(session, field) ? "originally: " + baselineText : null;
  }

  private field(label: string, originalHint: string | null, control: m.Children): m.Children {
    return m(".edit-field", [m("label", label), control, originalHint ? m("span.field-original", originalHint) : null]);
  }

  private cancel() {
    Routing.goToAccountSettings(this.accountName);
  }

  private load() {
    this.loading = true;
    this.error = undefined;
    accountEditSessions.load(this.accountName)
      .then((session) => {
        this.session = session;
        this.host = session.workingConfig.host;
        this.port = session.workingConfig.port;
        this.username = session.workingUsername;
        this.loading = false;
        m.redraw();
      })
      .catch((err: Error) => {
        this.loading = false;
        this.error = err.message;
        m.redraw();
      });
  }

  /** Mirrors the checks UpdateAccountApi itself makes — catches the common mistakes before they're staged into the session instead of only surfacing them at "Save Changes" time. */
  private validate(): string | undefined {
    if (!this.host.trim()) return "Host must not be blank.";
    if (this.port <= 0 || this.port > 65535) return "Port must be between 1 and 65535.";
    if (!this.username.trim()) return "Username must not be blank.";
    return undefined;
  }

  private apply() {
    const validationError = this.validate();
    if (validationError) {
      this.error = validationError;
      return;
    }
    const session = this.session!;
    session.workingConfig = { ...session.workingConfig, host: this.host, port: this.port };
    session.workingUsername = this.username;
    if (this.password !== "") session.workingPassword = this.password;
    Routing.goToAccountSettings(this.accountName);
  }
}
