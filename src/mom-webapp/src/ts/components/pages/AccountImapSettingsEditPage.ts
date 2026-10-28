import m from "mithril";
import { AbstractPage } from "./AbstractPage";
import { OAuthProvider } from "../../auto/pieroxy-mom";
import { Routing } from "../../utils/navigation/Routing";
import { TestImapConnectionButton } from "../TestImapConnectionButton";
import { MicrosoftSignIn } from "../MicrosoftSignIn";
import { AccountEditSession, accountEditSessions, isConfigFieldChanged, usesMicrosoftSignIn } from "../../utils/AccountEditSession";

interface AccountImapSettingsEditPageAttrs {
  accountName: string;
}

/**
 * Edits everything needed to connect to the mailbox — host, port, username, and the login: a
 * password or a Microsoft sign-in — grouped together since they're only ever useful as a whole
 * (see AccountSettingsPage's "IMAP settings" card). The password field always starts empty — the
 * server never sends the real one down (see AccountConfigApi's CredentialsInfoDto) — and stays
 * that way unless the user types a new one: leaving it blank means "keep whatever password is
 * already staged" (or the current one, if none is), never "erase it"; same rule
 * TestImapConnectionButton's password fallback uses. Switching from one login to the other takes
 * a new password or a new sign-in. Nothing is sent to the backend by "OK" itself: it just writes
 * the edited fields into the account's AccountEditSession and returns to the settings page, where
 * "Save Changes" persists the whole batch in one call.
 */
export class AccountImapSettingsEditPage extends AbstractPage<AccountImapSettingsEditPageAttrs> {
  private accountName = "";
  private session: AccountEditSession | undefined;
  private host = "";
  private port = 0;
  private username = "";
  private password = "";
  private microsoft = false;
  private clientId = "";
  private oauthSignInId = "";
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
          type: "text", value: this.username, autocomplete: "off",
          oninput: (e: Event) => (this.username = (e.target as HTMLInputElement).value),
        }),
        session.workingUsername !== session.baselineUsername
          ? m("span.field-original", "originally: " + session.baselineUsername) : null,
      ]),
      m(".edit-field", [
        m("label", "Login"),
        m("label.checkbox-field", [
          m("input", { type: "radio", name: "login", checked: !this.microsoft, onchange: () => (this.microsoft = false) }),
          "Password",
        ]),
        m("label.checkbox-field", [
          m("input", { type: "radio", name: "login", checked: this.microsoft, onchange: () => (this.microsoft = true) }),
          "Sign in with Microsoft (Outlook.com, Hotmail, Live, Microsoft 365)",
        ]),
      ]),
      this.microsoft ? this.renderMicrosoftLogin(session) : this.renderPasswordLogin(session),
      m(TestImapConnectionButton, {
        accountName: this.accountName, host: this.host, port: this.port,
        connectTimeout: session.workingConfig.connectTimeout, readTimeout: session.workingConfig.readTimeout, username: this.username,
        password: this.microsoft ? "" : this.password, oauthSignInId: this.microsoft ? this.oauthSignInId : "",
      }),
      m(".edit-actions", [
        m("button.ok-button", { onclick: () => this.apply() }, "OK"),
        m("button.cancel-button", { onclick: () => this.cancel() }, "Cancel"),
      ]),
    ]);
  }

  private renderPasswordLogin(session: AccountEditSession): m.Children {
    const savedPassword = !usesMicrosoftSignIn(session) || session.workingPassword !== "";
    return m(".edit-field", [
      m("label", "Password"),
      m("input", {
        type: "password", autocomplete: "new-password", value: this.password,
        placeholder: savedPassword ? "Leave blank to keep the current password" : "",
        oninput: (e: Event) => (this.password = (e.target as HTMLInputElement).value),
      }),
      savedPassword ? m("span.field-hint", "The current password is never shown here — leave this blank to keep it unchanged.") : null,
      session.workingPassword !== "" && this.password === ""
        ? m("span.field-original", "A new password is already staged for this account.") : null,
    ]);
  }

  private renderMicrosoftLogin(session: AccountEditSession): m.Children {
    const savedSignIn = usesMicrosoftSignIn(session);
    return [
      savedSignIn && this.oauthSignInId === ""
        ? m("p.field-hint", "This account signs in with Microsoft. Sign in again if MOM reports the sign-in expired or was revoked.")
        : null,
      m(MicrosoftSignIn, {
        clientId: this.clientId,
        onClientIdChange: (clientId) => (this.clientId = clientId),
        onSignedIn: (signInId, username) => {
          this.oauthSignInId = signInId;
          if (username && !this.username.trim()) this.username = username;
        },
      }),
    ];
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
        this.microsoft = usesMicrosoftSignIn(session);
        this.oauthSignInId = session.workingOauthSignInId;
        this.clientId = session.baselineOauthClientId;
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
  private validate(session: AccountEditSession): string | undefined {
    if (!this.host.trim()) return "Host must not be blank.";
    if (this.port <= 0 || this.port > 65535) return "Port must be between 1 and 65535.";
    if (!this.username.trim()) return "Username must not be blank.";
    if (this.microsoft && this.oauthSignInId === "" && session.baselineOauthProvider !== OAuthProvider.MICROSOFT) {
      return "Sign in with Microsoft first.";
    }
    if (!this.microsoft && this.password === "" && session.workingPassword === "" && session.baselineOauthProvider !== null) {
      return "Type the mailbox's password.";
    }
    return undefined;
  }

  private apply() {
    const session = this.session!;
    const validationError = this.validate(session);
    if (validationError) {
      this.error = validationError;
      return;
    }
    session.workingConfig = { ...session.workingConfig, host: this.host, port: this.port };
    session.workingUsername = this.username;
    if (this.microsoft) {
      session.workingOauthSignInId = this.oauthSignInId;
      session.workingPassword = "";
    } else {
      if (this.password !== "") session.workingPassword = this.password;
      session.workingOauthSignInId = "";
    }
    Routing.goToAccountSettings(this.accountName);
  }
}
