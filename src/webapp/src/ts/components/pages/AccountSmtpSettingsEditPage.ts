import m from "mithril";
import { AbstractPage } from "./AbstractPage";
import { Routing } from "../../utils/navigation/Routing";
import { SmtpSecurity, SmtpSettingsDto } from "../../auto/pieroxy-mom";
import { TestSmtpConnectionButton } from "../TestSmtpConnectionButton";
import { AccountEditSession, accountEditSessions } from "../../utils/AccountEditSession";

interface AccountSmtpSettingsEditPageAttrs {
  accountName: string;
}

const DEFAULT_PORTS: Record<SmtpSecurity, number> = { [SmtpSecurity.TLS]: 465, [SmtpSecurity.STARTTLS]: 587 };
const SECURITY_LABELS: Record<SmtpSecurity, string> = {
  [SmtpSecurity.TLS]: "TLS (usually port 465)",
  [SmtpSecurity.STARTTLS]: "STARTTLS (usually port 587)",
};

/**
 * Edits how the account sends mail. Same staging as AccountImapSettingsEditPage: "OK" only writes
 * into the AccountEditSession, "Save Changes" on the settings page persists. "Remove" stages "no
 * SMTP". The SMTP password field follows the same "blank keeps the current one" rule as IMAP's.
 */
export class AccountSmtpSettingsEditPage extends AbstractPage<AccountSmtpSettingsEditPageAttrs> {
  private accountName = "";
  private session: AccountEditSession | undefined;
  private smtp: SmtpSettingsDto = defaultSmtp("");
  private password = "";
  private loading = true;
  private error: string | undefined;

  getPageTitle(): string {
    return "MOM - Edit SMTP settings";
  }

  oninit({ attrs }: m.Vnode<AccountSmtpSettingsEditPageAttrs>) {
    this.accountName = attrs.accountName;
    this.load();
  }

  render(): m.Children {
    return m("page.accountsmtpsettingseditpage", [
      m(".page-header", [
        m("a.page-back", { onclick: () => this.cancel() }, "‹ Back to settings"),
        m("h1.page-title", "Edit SMTP settings — " + this.accountName),
      ]),
      this.error ? m(".settings-error.errorMessage", this.error) : null,
      this.loading || !this.session ? m(".page-loading", "Loading…") : this.renderForm(this.session),
    ]);
  }

  private renderForm(session: AccountEditSession): m.Children {
    const baseline = session.baselineSmtp;
    const hint = <K extends keyof SmtpSettingsDto>(field: K, text: (value: SmtpSettingsDto[K]) => string): string | null =>
      baseline && baseline[field] !== this.smtp[field] ? "originally: " + text(baseline[field]) : null;
    return m(".page-card.edit-form", [
      this.field("Host", hint("host", (v) => v), m("input", {
        type: "text", value: this.smtp.host,
        oninput: (e: Event) => (this.smtp.host = (e.target as HTMLInputElement).value),
      })),
      this.field("Security", hint("security", (v) => SECURITY_LABELS[v]), m("select", {
        value: this.smtp.security,
        onchange: (e: Event) => this.changeSecurity((e.target as HTMLSelectElement).value as SmtpSecurity),
      }, Object.values(SmtpSecurity).map((security) => m("option", { value: security }, SECURITY_LABELS[security])))),
      this.field("Port", hint("port", (v) => String(v)), m("input", {
        type: "number", value: this.smtp.port,
        oninput: (e: Event) => (this.smtp.port = Number((e.target as HTMLInputElement).value)),
      })),
      this.field("Sender address", hint("fromAddress", (v) => v), m("input", {
        type: "email", value: this.smtp.fromAddress,
        oninput: (e: Event) => (this.smtp.fromAddress = (e.target as HTMLInputElement).value),
      }), "The From address of the mail MOM sends for this account."),
      this.field("Login", hint("ownCredentials", (v) => v ? "own SMTP login" : "IMAP login"), m("label.checkbox-field", [
        m("input", {
          type: "checkbox", checked: !this.smtp.ownCredentials,
          onchange: (e: Event) => (this.smtp.ownCredentials = !(e.target as HTMLInputElement).checked),
        }),
        "Same as the IMAP login",
      ])),
      this.smtp.ownCredentials ? this.renderOwnCredentials(session) : null,
      m(TestSmtpConnectionButton, { session, smtp: this.smtp, password: this.password }),
      m(".edit-actions", [
        m("button.ok-button", { onclick: () => this.apply() }, "OK"),
        m("button.cancel-button", { onclick: () => this.cancel() }, "Cancel"),
        session.workingSmtp ? m("button.danger-button", { onclick: () => this.remove() }, "Remove SMTP settings") : null,
      ]),
    ]);
  }

  private renderOwnCredentials(session: AccountEditSession): m.Children {
    const baseline = session.baselineSmtp;
    const hasSavedPassword = !!baseline?.ownCredentials;
    return [
      this.field("SMTP username", baseline?.ownCredentials && baseline.username !== this.smtp.username ? "originally: " + baseline.username : null,
        m("input", {
          type: "text", value: this.smtp.username,
          oninput: (e: Event) => (this.smtp.username = (e.target as HTMLInputElement).value),
        })),
      m(".edit-field", [
        m("label", "SMTP password"),
        m("input", {
          type: "password", value: this.password,
          placeholder: hasSavedPassword ? "Leave blank to keep the current password" : "",
          oninput: (e: Event) => (this.password = (e.target as HTMLInputElement).value),
        }),
        hasSavedPassword ? m("span.field-hint", "The current password is never shown here — leave this blank to keep it unchanged.") : null,
        session.workingSmtpPassword !== "" && this.password === ""
          ? m("span.field-original", "A new password is already staged for this account.") : null,
      ]),
    ];
  }

  /** Follows the security mode's usual port, unless a non-standard one was typed. */
  private changeSecurity(security: SmtpSecurity) {
    if (Object.values(DEFAULT_PORTS).includes(this.smtp.port) || !this.smtp.port) this.smtp.port = DEFAULT_PORTS[security];
    this.smtp.security = security;
  }

  private field(label: string, originalHint: string | null, control: m.Children, fieldHint?: string): m.Children {
    return m(".edit-field", [
      m("label", label),
      control,
      fieldHint ? m("span.field-hint", fieldHint) : null,
      originalHint ? m("span.field-original", originalHint) : null,
    ]);
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
        this.smtp = session.workingSmtp ? { ...session.workingSmtp } : defaultSmtp(session.workingUsername);
        this.loading = false;
        m.redraw();
      })
      .catch((err: Error) => {
        this.loading = false;
        this.error = err.message;
        m.redraw();
      });
  }

  /** Mirrors the checks UpdateAccountApi and SettingsService#setSmtp make. */
  private validate(session: AccountEditSession): string | undefined {
    if (!this.smtp.host.trim()) return "Host must not be blank.";
    if (this.smtp.port <= 0 || this.smtp.port > 65535) return "Port must be between 1 and 65535.";
    if (!/^[^@\s]+@[^@\s]+$/.test(this.smtp.fromAddress.trim())) return "The sender address must be a valid email address.";
    if (this.smtp.ownCredentials) {
      if (!this.smtp.username.trim()) return "SMTP username must not be blank.";
      if (!session.baselineSmtp?.ownCredentials && this.password === "" && session.workingSmtpPassword === "") {
        return "The SMTP password must not be blank.";
      }
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
    session.workingSmtp = {
      ...this.smtp,
      host: this.smtp.host.trim(),
      fromAddress: this.smtp.fromAddress.trim(),
      username: this.smtp.ownCredentials ? this.smtp.username : "",
    };
    if (!this.smtp.ownCredentials) session.workingSmtpPassword = "";
    else if (this.password !== "") session.workingSmtpPassword = this.password;
    Routing.goToAccountSettings(this.accountName);
  }

  private remove() {
    const session = this.session!;
    session.workingSmtp = null;
    session.workingSmtpPassword = "";
    Routing.goToAccountSettings(this.accountName);
  }
}

function defaultSmtp(imapUsername: string): SmtpSettingsDto {
  return {
    host: "",
    port: DEFAULT_PORTS[SmtpSecurity.STARTTLS],
    security: SmtpSecurity.STARTTLS,
    fromAddress: imapUsername.includes("@") ? imapUsername : "",
    ownCredentials: false,
    username: "",
  };
}
