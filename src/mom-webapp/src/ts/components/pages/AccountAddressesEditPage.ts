import m from "mithril";
import { AbstractPage } from "./AbstractPage";
import { Routing } from "../../utils/navigation/Routing";
import { AccountAddressesForm, normalizeAddresses, validateAddresses } from "../AccountAddressesForm";
import { AccountEditSession, accountEditSessions, isConfigFieldChanged } from "../../utils/AccountEditSession";

interface AccountAddressesEditPageAttrs {
  accountName: string;
}

/** Edits the account's addresses. Same staging as AccountGeneralEditPage: "OK" only writes into the AccountEditSession. */
export class AccountAddressesEditPage extends AbstractPage<AccountAddressesEditPageAttrs> {
  private accountName = "";
  private session: AccountEditSession | undefined;
  private addresses: string[] = [];
  private loading = true;
  private error: string | undefined;

  getPageTitle(): string {
    return "MOM - Edit addresses";
  }

  oninit({ attrs }: m.Vnode<AccountAddressesEditPageAttrs>) {
    this.accountName = attrs.accountName;
    this.load();
  }

  render(): m.Children {
    return m("page.accountaddresseseditpage", [
      m(".page-header", [
        m("a.page-back", { onclick: () => this.cancel() }, "‹ Back to settings"),
        m("h1.page-title", "Edit addresses — " + this.accountName),
      ]),
      this.error ? m(".settings-error.errorMessage", this.error) : null,
      this.loading || !this.session ? m(".page-loading", "Loading…") : this.renderForm(this.session),
    ]);
  }

  private renderForm(session: AccountEditSession): m.Children {
    return m(".page-card.edit-form", [
      m(AccountAddressesForm, {
        addresses: this.addresses,
        onChange: (addresses) => (this.addresses = addresses),
        originalHint: isConfigFieldChanged(session, "addresses")
          ? "originally: " + (session.baselineConfig.addresses.join(", ") || "none") : null,
      }),
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
        this.addresses = [...session.workingConfig.addresses];
        this.loading = false;
        m.redraw();
      })
      .catch((err: Error) => {
        this.loading = false;
        this.error = err.message;
        m.redraw();
      });
  }

  private apply() {
    const validationError = validateAddresses(this.addresses);
    if (validationError) {
      this.error = validationError;
      return;
    }
    const session = this.session!;
    session.workingConfig = { ...session.workingConfig, addresses: normalizeAddresses(this.addresses) };
    Routing.goToAccountSettings(this.accountName);
  }
}
