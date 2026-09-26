import m from "mithril";
import { ApiEndpoints } from "../../auto/ApiEndpoints";
import { AbstractPage } from "./AbstractPage";
import { Routing } from "../../utils/navigation/Routing";

interface AccountCredentialsEditPageAttrs {
  accountName: string;
}

/**
 * Edits the account's IMAP username/password. The password field always starts empty — the
 * server never sends the real one down (see AccountConfigApi's CredentialsInfoDto) — and stays
 * that way unless the user types a new one: leaving it blank on Save means "keep the current
 * password", never "erase it" (see UpdateAccountCredentialsApi). Saving restarts the account;
 * both Save and Cancel return to the read-only settings page.
 */
export class AccountCredentialsEditPage extends AbstractPage<AccountCredentialsEditPageAttrs> {
  private accountName = "";
  private username = "";
  private password = "";
  private loading = true;
  private saving = false;
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
    return m(".page-card.edit-form", [
      m(".edit-field", [
        m("label", "Username"),
        m("input", {
          type: "text", value: this.username, disabled: this.saving,
          oninput: (e: Event) => (this.username = (e.target as HTMLInputElement).value),
        }),
      ]),
      m(".edit-field", [
        m("label", "Password"),
        m("input", {
          type: "password", value: this.password, placeholder: "Leave blank to keep the current password", disabled: this.saving,
          oninput: (e: Event) => (this.password = (e.target as HTMLInputElement).value),
        }),
        m("span.field-hint", "The current password is never shown here — leave this blank to keep it unchanged."),
      ]),
      m(".edit-actions", [
        m("button.save-button", { onclick: () => this.save(), disabled: this.saving }, this.saving ? "Saving…" : "Save"),
        m("button.cancel-button", { onclick: () => this.cancel(), disabled: this.saving }, "Cancel"),
      ]),
    ]);
  }

  private cancel() {
    Routing.goToAccountSettings(this.accountName);
  }

  private load() {
    this.loading = true;
    this.error = undefined;
    ApiEndpoints.AccountConfig.call({ accountName: this.accountName })
      .then((output) => {
        this.username = output.credentials.username;
        this.loading = false;
        m.redraw();
      })
      .catch((err: Error) => {
        this.loading = false;
        this.error = err.message;
        m.redraw();
      });
  }

  private save() {
    this.saving = true;
    this.error = undefined;
    ApiEndpoints.UpdateAccountCredentials.call({
      accountName: this.accountName,
      username: this.username,
      password: this.password,
    })
      .then(() => {
        this.saving = false;
        Routing.goToAccountSettings(this.accountName);
      })
      .catch((err: Error) => {
        this.saving = false;
        this.error = err.message;
        m.redraw();
      });
  }
}
