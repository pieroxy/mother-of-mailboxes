import m from "mithril";
import { AbstractPage } from "./AbstractPage";
import { Routing } from "../../utils/navigation/Routing";
import { SmtpSettingsDto } from "../../auto/pieroxy-mom";
import { TestSmtpConnectionButton } from "../TestSmtpConnectionButton";
import { SECURITY_LABELS, SmtpSettingsForm, defaultSmtp, finalizeSmtp, validateSmtp } from "../SmtpSettingsForm";
import { AccountEditSession, accountEditSessions } from "../../utils/AccountEditSession";

interface AccountSmtpSettingsEditPageAttrs {
  accountName: string;
}

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
    const hasSavedPassword = !!baseline?.ownCredentials;
    return m(".page-card.edit-form", [
      m(SmtpSettingsForm, {
        smtp: this.smtp,
        password: this.password,
        onPasswordChange: (password) => (this.password = password),
        originalHint: (field) => this.originalHint(baseline, field),
        passwordPlaceholder: hasSavedPassword ? "Leave blank to keep the current password" : "",
        passwordHints: [
          hasSavedPassword ? m("span.field-hint", "The current password is never shown here — leave this blank to keep it unchanged.") : null,
          session.workingSmtpPassword !== "" && this.password === ""
            ? m("span.field-original", "A new password is already staged for this account.") : null,
        ],
      }),
      m(TestSmtpConnectionButton, {
        accountName: session.accountName, smtp: this.smtp, password: this.password,
        imapUsername: session.workingUsername, imapPassword: session.workingPassword,
        connectTimeout: session.workingConfig.connectTimeout, readTimeout: session.workingConfig.readTimeout,
      }),
      m(".edit-actions", [
        m("button.ok-button", { onclick: () => this.apply() }, "OK"),
        m("button.cancel-button", { onclick: () => this.cancel() }, "Cancel"),
        session.workingSmtp ? m("button.danger-button", { onclick: () => this.remove() }, "Remove SMTP settings") : null,
      ]),
    ]);
  }

  private originalHint(baseline: SmtpSettingsDto | null, field: keyof SmtpSettingsDto): string | null {
    if (!baseline) return null;
    switch (field) {
      case "security": return baseline.security !== this.smtp.security ? "originally: " + SECURITY_LABELS[baseline.security] : null;
      case "port": return baseline.port !== this.smtp.port ? "originally: " + baseline.port : null;
      case "aliases": return JSON.stringify(baseline.aliases) !== JSON.stringify(this.smtp.aliases)
        ? "originally: " + (baseline.aliases.join(", ") || "none") : null;
      case "ownCredentials": return baseline.ownCredentials !== this.smtp.ownCredentials
        ? "originally: " + (baseline.ownCredentials ? "own SMTP login" : "IMAP login") : null;
      case "username": return baseline.ownCredentials && baseline.username !== this.smtp.username ? "originally: " + baseline.username : null;
      case "host": return baseline.host !== this.smtp.host ? "originally: " + baseline.host : null;
      case "fromAddress": return baseline.fromAddress !== this.smtp.fromAddress ? "originally: " + baseline.fromAddress : null;
    }
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
        this.smtp = session.workingSmtp ? { ...session.workingSmtp, aliases: [...session.workingSmtp.aliases] } : defaultSmtp(session.workingUsername);
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
    const passwordMissing = !session.baselineSmtp?.ownCredentials && this.password === "" && session.workingSmtpPassword === "";
    return validateSmtp(this.smtp, passwordMissing);
  }

  private apply() {
    const session = this.session!;
    const validationError = this.validate(session);
    if (validationError) {
      this.error = validationError;
      return;
    }
    session.workingSmtp = finalizeSmtp(this.smtp);
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
