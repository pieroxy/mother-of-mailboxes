import m from "mithril";
import { AbstractPage } from "./AbstractPage";
import { Routing } from "../../utils/navigation/Routing";
import { TokenListEditor } from "../TokenListEditor";
import { AccountEditSession, accountEditSessions, isConfigFieldChanged } from "../../utils/AccountEditSession";

interface AccountGeneralEditPageAttrs {
  accountName: string;
}

/**
 * Edits the General section's fields (see AccountSettingsPage/AccountBasicConfigDto) — everything
 * except displayName, which stays read-only: it names every one of this account's on-disk files
 * (state, learned rules, classifier corpus, stats), so editing it here would silently orphan that
 * history instead of migrating it. Host/port live on AccountImapSettingsEditPage instead, grouped
 * with the IMAP credentials they connect with. Nothing is sent to the backend here: "OK" just
 * writes the edited fields into the account's AccountEditSession and returns to the settings page,
 * where they show highlighted until "Save Changes" persists the whole batch in one call.
 */
export class AccountGeneralEditPage extends AbstractPage<AccountGeneralEditPageAttrs> {
  private accountName = "";
  private session: AccountEditSession | undefined;
  private displayName = "";
  private runEvery = 0;
  private classifierSpamFolderName = "";
  private classifierExcludedFolders: string[] = [];
  private classifierCorpusRetentionDays = 0;
  private classifierCorpusScanBatchSize = 0;
  private discoveryTreeDisabled = false;
  private loading = true;
  private error: string | undefined;

  getPageTitle(): string {
    return "MOM - Edit general settings";
  }

  oninit({ attrs }: m.Vnode<AccountGeneralEditPageAttrs>) {
    this.accountName = attrs.accountName;
    this.load();
  }

  render(): m.Children {
    return m("page.accountgeneraleditpage", [
      m(".page-header", [
        m("a.page-back", { onclick: () => this.cancel() }, "‹ Back to settings"),
        m("h1.page-title", "Edit general settings — " + this.accountName),
      ]),
      this.error ? m(".settings-error.errorMessage", this.error) : null,
      this.loading ? m(".page-loading", "Loading…") : this.renderForm(),
    ]);
  }

  private renderForm(): m.Children {
    const session = this.session!;
    const baseline = session.baselineConfig;
    return m(".page-card.edit-form", [
      this.field("Display name", null, m("input", { type: "text", value: this.displayName, disabled: true })),
      this.field("Run every (seconds)", this.originalHint(session, "runEvery", String(baseline.runEvery)), m("input", {
        type: "number", value: this.runEvery,
        oninput: (e: Event) => (this.runEvery = Number((e.target as HTMLInputElement).value)),
      })),
      this.field("Spam folder", this.originalHint(session, "classifierSpamFolderName", baseline.classifierSpamFolderName || "Spam (default)"), m("input", {
        type: "text", value: this.classifierSpamFolderName, placeholder: "Spam (default)",
        oninput: (e: Event) => (this.classifierSpamFolderName = (e.target as HTMLInputElement).value),
      })),
      this.field("Classifier excluded folders", this.originalHint(session, "classifierExcludedFolders", baseline.classifierExcludedFolders.join(", ") || "none"), m(TokenListEditor, {
        tokens: this.classifierExcludedFolders,
        onChange: (folders) => (this.classifierExcludedFolders = folders),
        placeholder: "Folder name",
      })),
      this.field("Classifier corpus retention (days, 0 = disabled)", this.originalHint(session, "classifierCorpusRetentionDays", String(baseline.classifierCorpusRetentionDays)), m("input", {
        type: "number", value: this.classifierCorpusRetentionDays,
        oninput: (e: Event) => (this.classifierCorpusRetentionDays = Number((e.target as HTMLInputElement).value)),
      })),
      this.field("Classifier scan batch size (0 = default)", this.originalHint(session, "classifierCorpusScanBatchSize", String(baseline.classifierCorpusScanBatchSize)), m("input", {
        type: "number", value: this.classifierCorpusScanBatchSize,
        oninput: (e: Event) => (this.classifierCorpusScanBatchSize = Number((e.target as HTMLInputElement).value)),
      })),
      this.field("Discovery tree", this.originalHint(session, "discoveryTreeDisabled", baseline.discoveryTreeDisabled ? "disabled" : "enabled"), m("label.checkbox-field", [
        m("input", {
          type: "checkbox", checked: this.discoveryTreeDisabled,
          onchange: (e: Event) => (this.discoveryTreeDisabled = (e.target as HTMLInputElement).checked),
        }),
        "Disabled",
      ])),
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
        this.applyFromWorkingConfig(session);
        this.loading = false;
        m.redraw();
      })
      .catch((err: Error) => {
        this.loading = false;
        this.error = err.message;
        m.redraw();
      });
  }

  private applyFromWorkingConfig(session: AccountEditSession) {
    const config = session.workingConfig;
    this.displayName = config.displayName;
    this.runEvery = config.runEvery;
    this.classifierSpamFolderName = config.classifierSpamFolderName || "";
    this.classifierExcludedFolders = config.classifierExcludedFolders;
    this.classifierCorpusRetentionDays = config.classifierCorpusRetentionDays;
    this.classifierCorpusScanBatchSize = config.classifierCorpusScanBatchSize;
    this.discoveryTreeDisabled = config.discoveryTreeDisabled;
  }

  /** Mirrors the checks UpdateAccountApi itself makes — catches the common mistakes before they're staged into the session instead of only surfacing them at "Save Changes" time. */
  private validate(): string | undefined {
    if (this.runEvery <= 0) return "\"Run every\" must be a positive number of seconds.";
    return undefined;
  }

  private apply() {
    const validationError = this.validate();
    if (validationError) {
      this.error = validationError;
      return;
    }
    const session = this.session!;
    session.workingConfig = {
      ...session.workingConfig,
      runEvery: this.runEvery,
      classifierSpamFolderName: this.classifierSpamFolderName,
      classifierExcludedFolders: this.classifierExcludedFolders,
      classifierCorpusRetentionDays: this.classifierCorpusRetentionDays,
      classifierCorpusScanBatchSize: this.classifierCorpusScanBatchSize,
      discoveryTreeDisabled: this.discoveryTreeDisabled,
    };
    Routing.goToAccountSettings(this.accountName);
  }
}
