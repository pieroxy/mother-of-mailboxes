import m from "mithril";
import { ApiEndpoints } from "../../auto/ApiEndpoints";
import { AccountBasicConfigDto } from "../../auto/pieroxy-mom";
import { AbstractPage } from "./AbstractPage";
import { Routing } from "../../utils/navigation/Routing";

interface AccountConfigEditPageAttrs {
  accountName: string;
}

/**
 * Edits the Config section's fields (see AccountSettingsPage/AccountBasicConfigDto) — everything
 * except displayName, which stays read-only: it names every one of this account's on-disk files
 * (state, learned rules, classifier corpus, stats), so editing it here would silently orphan that
 * history instead of migrating it (see UpdateAccountConfigApi). Saving restarts the account so
 * the change takes effect right away; both Save and Cancel return to the read-only settings page.
 */
export class AccountConfigEditPage extends AbstractPage<AccountConfigEditPageAttrs> {
  private accountName = "";
  private displayName = "";
  private host = "";
  private port = 0;
  private runEvery = 0;
  private classifierSpamFolderName = "";
  private classifierExcludedFolders: string[] = [];
  private newFolder = "";
  private classifierCorpusRetentionDays = 0;
  private classifierCorpusScanBatchSize = 0;
  private discoveryTreeDisabled = false;
  private loading = true;
  private saving = false;
  private error: string | undefined;

  getPageTitle(): string {
    return "MOM - Edit config";
  }

  oninit({ attrs }: m.Vnode<AccountConfigEditPageAttrs>) {
    this.accountName = attrs.accountName;
    this.load();
  }

  render(): m.Children {
    return m("page.accountconfigeditpage", [
      m(".page-header", [
        m("a.page-back", { onclick: () => this.cancel() }, "‹ Back to settings"),
        m("h1.page-title", "Edit config — " + this.accountName),
      ]),
      this.error ? m(".settings-error.errorMessage", this.error) : null,
      this.loading ? m(".page-loading", "Loading…") : this.renderForm(),
    ]);
  }

  private renderForm(): m.Children {
    return m(".page-card.edit-form", [
      this.field("Display name", m("input", { type: "text", value: this.displayName, disabled: true })),
      this.field("Host", m("input", {
        type: "text", value: this.host, disabled: this.saving,
        oninput: (e: Event) => (this.host = (e.target as HTMLInputElement).value),
      })),
      this.field("Port", m("input", {
        type: "number", value: this.port, disabled: this.saving,
        oninput: (e: Event) => (this.port = Number((e.target as HTMLInputElement).value)),
      })),
      this.field("Run every (seconds)", m("input", {
        type: "number", value: this.runEvery, disabled: this.saving,
        oninput: (e: Event) => (this.runEvery = Number((e.target as HTMLInputElement).value)),
      })),
      this.field("Spam folder", m("input", {
        type: "text", value: this.classifierSpamFolderName, placeholder: "Spam (default)", disabled: this.saving,
        oninput: (e: Event) => (this.classifierSpamFolderName = (e.target as HTMLInputElement).value),
      })),
      this.field("Classifier excluded folders", this.renderFolderEditor()),
      this.field("Classifier corpus retention (days, 0 = disabled)", m("input", {
        type: "number", value: this.classifierCorpusRetentionDays, disabled: this.saving,
        oninput: (e: Event) => (this.classifierCorpusRetentionDays = Number((e.target as HTMLInputElement).value)),
      })),
      this.field("Classifier scan batch size (0 = default)", m("input", {
        type: "number", value: this.classifierCorpusScanBatchSize, disabled: this.saving,
        oninput: (e: Event) => (this.classifierCorpusScanBatchSize = Number((e.target as HTMLInputElement).value)),
      })),
      this.field("Discovery tree", m("label.checkbox-field", [
        m("input", {
          type: "checkbox", checked: this.discoveryTreeDisabled, disabled: this.saving,
          onchange: (e: Event) => (this.discoveryTreeDisabled = (e.target as HTMLInputElement).checked),
        }),
        "Disabled",
      ])),
      m(".edit-actions", [
        m("button.save-button", { onclick: () => this.save(), disabled: this.saving }, this.saving ? "Saving…" : "Save"),
        m("button.cancel-button", { onclick: () => this.cancel(), disabled: this.saving }, "Cancel"),
      ]),
    ]);
  }

  private renderFolderEditor(): m.Children {
    return m(".folder-editor", [
      m(".token-list", this.classifierExcludedFolders.map((folder) => m(".token", { key: folder }, [
        folder,
        m("span.token-remove", { onclick: () => this.removeFolder(folder) }, "×"),
      ]))),
      m(".folder-editor-add", [
        m("input", {
          type: "text", value: this.newFolder, placeholder: "Folder name", disabled: this.saving,
          oninput: (e: Event) => (this.newFolder = (e.target as HTMLInputElement).value),
          onkeydown: (e: KeyboardEvent) => {
            if (e.key === "Enter") {
              e.preventDefault();
              this.addFolder();
            }
          },
        }),
        m("button.add-folder-button", { onclick: () => this.addFolder(), disabled: this.saving }, "Add"),
      ]),
    ]);
  }

  private field(label: string, control: m.Children): m.Children {
    return m(".edit-field", [m("label", label), control]);
  }

  private addFolder() {
    const folder = this.newFolder.trim();
    if (folder && !this.classifierExcludedFolders.includes(folder)) {
      this.classifierExcludedFolders = [...this.classifierExcludedFolders, folder];
    }
    this.newFolder = "";
  }

  private removeFolder(folder: string) {
    this.classifierExcludedFolders = this.classifierExcludedFolders.filter((f) => f !== folder);
  }

  private cancel() {
    Routing.goToAccountSettings(this.accountName);
  }

  private load() {
    this.loading = true;
    this.error = undefined;
    ApiEndpoints.AccountConfig.call({ accountName: this.accountName })
      .then((output) => {
        this.applyConfig(output.config);
        this.loading = false;
        m.redraw();
      })
      .catch((err: Error) => {
        this.loading = false;
        this.error = err.message;
        m.redraw();
      });
  }

  private applyConfig(config: AccountBasicConfigDto) {
    this.displayName = config.displayName;
    this.host = config.host;
    this.port = config.port;
    this.runEvery = config.runEvery;
    this.classifierSpamFolderName = config.classifierSpamFolderName || "";
    this.classifierExcludedFolders = config.classifierExcludedFolders;
    this.classifierCorpusRetentionDays = config.classifierCorpusRetentionDays;
    this.classifierCorpusScanBatchSize = config.classifierCorpusScanBatchSize;
    this.discoveryTreeDisabled = config.discoveryTreeDisabled;
  }

  private save() {
    this.saving = true;
    this.error = undefined;
    ApiEndpoints.UpdateAccountConfig.call({
      accountName: this.accountName,
      host: this.host,
      port: this.port,
      runEvery: this.runEvery,
      classifierSpamFolderName: this.classifierSpamFolderName,
      classifierExcludedFolders: this.classifierExcludedFolders,
      classifierCorpusRetentionDays: this.classifierCorpusRetentionDays,
      classifierCorpusScanBatchSize: this.classifierCorpusScanBatchSize,
      discoveryTreeDisabled: this.discoveryTreeDisabled,
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
