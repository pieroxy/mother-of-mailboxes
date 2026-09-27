import m from "mithril";
import { AbstractPage } from "./AbstractPage";
import { Routing } from "../../utils/navigation/Routing";
import { AccountEditSession, accountEditSessions } from "../../utils/AccountEditSession";

interface AccountCredentialsEditPageAttrs {
  accountName: string;
}

/**
 * Edits the account's IMAP username/password. The password field always starts empty — the
 * server never sends the real one down (see AccountConfigApi's CredentialsInfoDto) — and stays
 * that way unless the user types a new one: leaving it blank means "keep whatever password is
 * already staged" (or the current one, if none is), never "erase it". Nothing is sent to the
 * backend here: "OK" just writes the edited username/password into the account's
 * AccountEditSession and returns to the settings page, where "Save Changes" persists the whole
 * batch in one call.
 */
export class AccountCredentialsEditPage extends AbstractPage<AccountCredentialsEditPageAttrs> {
  private accountName = "";
  private session: AccountEditSession | undefined;
  private username = "";
  private password = "";
  private loading = true;
  private error: string | undefined;

  getPageTitle(): string {
    return "MOM - Edit credentials";
  }

  oninit({ attrs }: m.Vnode<AccountCredentialsEditPageAttrs>) {
    this.accountName = attrs.accountName;
    this.load();
  }

  render(): m.Children {
    return m("page.accountcredentialseditpage", [
      m(".page-header", [
        m("a.page-back", { onclick: () => this.cancel() }, "‹ Back to settings"),
        m("h1.page-title", "Edit credentials — " + this.accountName),
      ]),
      this.error ? m(".settings-error.errorMessage", this.error) : null,
      this.loading ? m(".page-loading", "Loading…") : this.renderForm(),
    ]);
  }

  private renderForm(): m.Children {
    const session = this.session!;
    return m(".page-card.edit-form", [
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
      m(".edit-actions", [
        m("button.ok-button", { onclick: () => this.apply() }, "OK"),
        m("button.cancel-button", { onclick: () => this.cancel() }, "Cancel"),
      ]),
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

  private validate(): string | undefined {
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
    session.workingUsername = this.username;
    if (this.password !== "") session.workingPassword = this.password;
    Routing.goToAccountSettings(this.accountName);
  }
}
