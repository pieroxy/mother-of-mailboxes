import m from "mithril";
import { ApiEndpoints } from "../../auto/ApiEndpoints";
import { ReputationListDto, ReputationListType } from "../../auto/pieroxy-mom";
import { AbstractPage } from "./AbstractPage";
import { Routing } from "../../utils/navigation/Routing";
import { Endpoints } from "../../utils/navigation/Endpoints";
import { DeleteIcon } from "../atoms/icons/DeleteIcon";
import { Dialogs } from "../../utils/Dialogs";
import { WebServerMovedDialog } from "../WebServerMovedDialog";
import { Notification, Notifications, NotificationsClass, NotificationsType } from "../../utils/Notifications";

/** One reputation list in the page's working list, tracking enough to render "New"/"Edited"/"Deleted" and to rebuild the final array on save. */
interface PendingReputationList {
  list: ReputationListDto;
  originalIndex: number | null;
  deleted: boolean;
  edited: boolean;
}

function defaultReputationList(): ReputationListDto {
  // lastRefreshTimestamp/itemCount/contentSizeBytes: live dashboard fields (see AccountsApi-style
  // HomePage section), meaningless before this list has ever been saved and fetched — "" / 0 / 0
  // accurately represents that, not a placeholder standing in for real data.
  return {
    id: "", type: ReputationListType.IP_CIDR, url: "", refreshHours: 24, score: 1,
    lastRefreshTimestamp: "", itemCount: 0, contentSizeBytes: 0,
  };
}

/**
 * The parts of config.json that aren't any one account's own settings (reached via the cog icon
 * next to the profile icon, top-right). Like every other settings page, edits are staged until
 * "Save Changes" (UpdateGeneralSettingsApi) — except the Web Server section, whose buttons each
 * apply one change immediately: moving or stopping the server the page is served from can't wait
 * for, or be undone by, a later save.
 */
export class GeneralSettingsPage extends AbstractPage {
  private baselineDataFolder = "";
  private workingDataFolder = "";
  private baselineKeepLogFiles = 0;
  private workingKeepLogFiles = 0;
  private webServerEnabled = false;
  private webServerHttpPort = 0;
  private webServerAddress = "";
  private webServerCredentialsKey = "";

  private baselineUsername = "";
  private workingUsername = "";
  private workingPassword = "";

  private reputationLists: PendingReputationList[] = [];

  private loading = true;
  private saving = false;
  private error: string | undefined;

  getPageTitle(): string {
    return "MOM - General settings";
  }

  oninit() {
    this.load();
  }

  render(): m.Children {
    return m("page.generalsettingspage", [
      m(".page-header", [
        m("a.page-back", { onclick: () => Routing.goToScreen(Endpoints.HOME) }, "‹ Back to accounts"),
        m("h1.page-title", "General settings"),
      ]),
      this.error ? m(".settings-error.errorMessage", this.error) : null,
      this.loading ? m(".page-loading", "Loading…") : this.renderContent(),
    ]);
  }

  private renderContent(): m.Children {
    return m(".settings-content", [
      this.isDirty() ? this.renderChangesBar() : null,
      m(".page-card", [m("h2", "Server"), this.renderServerSection()]),
      m(".page-card", [m("h2", "Web Server"), this.renderWebServerSection()]),
      m(".page-card", [m("h2", "Web Login"), this.renderWebLoginSection()]),
      m(".page-card", [
        m("h2", "Reputation Lists"),
        this.renderReputationListsSection(),
        m("button.add-item-button", { onclick: () => this.addReputationList() }, "+ Add reputation list"),
      ]),
    ]);
  }

  private renderChangesBar(): m.Children {
    return m(".settings-changes-bar", [
      m("span", "You have unsaved changes."),
      m(".changes-actions", [
        m("button.save-changes-button", { onclick: () => this.saveChanges(), disabled: this.saving },
          this.saving ? "Saving…" : "Save Changes"),
        m("button.discard-changes-button", { onclick: () => this.load(), disabled: this.saving }, "Discard Changes"),
      ]),
    ]);
  }

  private renderServerSection(): m.Children {
    return m(".edit-form", [
      this.field("Data folder", m("input", {
        type: "text", value: this.workingDataFolder,
        oninput: (e: Event) => (this.workingDataFolder = (e.target as HTMLInputElement).value),
      })),
      this.field("Keep log files (days, 0 = disabled)", m("input", {
        type: "number", value: this.workingKeepLogFiles, min: 0,
        oninput: (e: Event) => (this.workingKeepLogFiles = Number((e.target as HTMLInputElement).value)),
      })),
      m(".field-hint", "Saved to config.json, but only taken into account once MOM is restarted by hand."),
    ]);
  }

  /** Read-only values: each button applies its change immediately, outside "Save Changes". */
  private renderWebServerSection(): m.Children {
    return m(".config-grid", [
      actionRow("Status", this.webServerEnabled ? "Enabled" : "Disabled",
        this.webServerEnabled ? m("button.danger-button", { onclick: () => this.disableWebServer() }, "Disable") : null),
      actionRow("Port", String(this.webServerHttpPort),
        m("button.secondary", { onclick: () => this.changeWebServerPort() }, "Change")),
      actionRow("Address", this.webServerAddress || m("span.config-row-hint", "(all interfaces)"),
        m("button.secondary", { onclick: () => this.changeWebServerAddress() }, "Change")),
    ]);
  }

  private changeWebServerPort() {
    Dialogs.prompt("New web server port:", String(this.webServerHttpPort), (value) => {
      const port = Number(value.trim());
      if (!Number.isInteger(port) || port < 1 || port > 65535) return new Error("The port must be a number between 1 and 65535.");
      if (port === this.webServerHttpPort) return new Error("That's already the current port.");
      return port;
    }, (port) => {
      const oldPort = this.webServerHttpPort;
      ApiEndpoints.ChangeWebServerPort.call({ port })
        .then(() => {
          this.webServerHttpPort = port;
          Dialogs.add(new WebServerMovedDialog(oldPort, port));
          m.redraw();
        })
        .catch((err: Error) => {
          Notifications.addNotification(new Notification(NotificationsClass.WEB_SERVER_CHANGE, NotificationsType.ERROR, err.message, 8));
          m.redraw();
        });
    });
  }

  private changeWebServerAddress() {
    Dialogs.prompt("New web server address (leave empty for all interfaces):", this.webServerAddress, (value) => {
      const address = value.trim();
      if (/\s/.test(address)) return new Error("The address must not contain spaces.");
      if (address === this.webServerAddress) return new Error("That's already the current address.");
      return address;
    }, () => notImplementedYet());
  }

  private disableWebServer() {
    Dialogs.confirm("Disable the web server? This page will stop responding right away. To bring it back, set "
      + "webServer.enabled to true in config.json and restart MOM.", "Disable", "Cancel", () => notImplementedYet());
  }

  private renderWebLoginSection(): m.Children {
    return m(".edit-form", [
      configRow("Credentials key", this.webServerCredentialsKey),
      this.field("Username", m("input", {
        type: "text", value: this.workingUsername,
        oninput: (e: Event) => (this.workingUsername = (e.target as HTMLInputElement).value),
      })),
      this.field("Password", [
        m("input", {
          type: "password", value: this.workingPassword, placeholder: "Leave blank to keep the current password",
          oninput: (e: Event) => (this.workingPassword = (e.target as HTMLInputElement).value),
        }),
        m("span.field-hint", "The current password is never shown here — leave this blank to keep it unchanged."),
      ]),
    ]);
  }

  private renderReputationListsSection(): m.Children {
    if (this.reputationLists.length === 0) return m(".settings-empty", "No reputation lists configured.");
    return m(".rule-list", this.reputationLists.map((pending, index) => this.renderReputationList(pending, index)));
  }

  private renderReputationList(pending: PendingReputationList, index: number): m.Children {
    const list = pending.list;
    const isDeleted = pending.deleted;
    const header = m(".rule-card-header", [
      m(".rule-card-header-left", [
        m(".rule-index", list.id || "(new reputation list)"),
        this.renderReputationListTags(pending),
      ]),
      m(".rule-move-controls", [
        m("span.rule-delete-button" + (isDeleted ? ".active" : ""),
          { title: isDeleted ? "Restore this list" : "Delete this list", onclick: () => this.toggleDeleteReputationList(pending) }, m(DeleteIcon)),
      ]),
    ]);
    const body = m(".edit-form", [
      this.field("Id", m("input", {
        type: "text", value: list.id, disabled: isDeleted,
        oninput: (e: Event) => this.updateReputationList(pending, { id: (e.target as HTMLInputElement).value }),
      })),
      this.field("Type", m("select", {
        value: list.type, disabled: isDeleted,
        onchange: (e: Event) => this.updateReputationList(pending, { type: (e.target as HTMLSelectElement).value as ReputationListType }),
      }, [
        m("option", { value: ReputationListType.IP_CIDR }, "IP / CIDR"),
        m("option", { value: ReputationListType.DOMAIN }, "Domain"),
      ])),
      this.field("URL", m("input", {
        type: "text", value: list.url, disabled: isDeleted, placeholder: "https://... or file://...",
        oninput: (e: Event) => this.updateReputationList(pending, { url: (e.target as HTMLInputElement).value }),
      })),
      this.field("Refresh (hours)", m("input", {
        type: "number", value: list.refreshHours, disabled: isDeleted, min: 1,
        oninput: (e: Event) => this.updateReputationList(pending, { refreshHours: Number((e.target as HTMLInputElement).value) }),
      })),
      this.field("Score (0 = ok, 1 = spam)", m("input", {
        type: "number", value: list.score, disabled: isDeleted, min: 0, max: 1, step: 0.05,
        oninput: (e: Event) => this.updateReputationList(pending, { score: Number((e.target as HTMLInputElement).value) }),
      })),
    ]);
    return m(".rule-card" + (isDeleted ? ".rule-deleted" : ""), { key: index }, [header, body]);
  }

  private renderReputationListTags(pending: PendingReputationList): m.Children {
    const tags: m.Children[] = [];
    if (pending.originalIndex === null) tags.push(m("span.rule-tag", { key: "new" }, "New"));
    if (pending.edited) tags.push(m("span.rule-tag", { key: "edited" }, "Edited"));
    return tags.length > 0 ? m(".rule-tags", tags) : null;
  }

  private field(label: string, control: m.Children): m.Children {
    return m(".edit-field", [m("label", label), control]);
  }

  private updateReputationList(pending: PendingReputationList, changes: Partial<ReputationListDto>) {
    pending.list = { ...pending.list, ...changes };
    pending.edited = true;
  }

  private toggleDeleteReputationList(pending: PendingReputationList) {
    pending.deleted = !pending.deleted;
  }

  private addReputationList() {
    this.reputationLists = [...this.reputationLists, { list: defaultReputationList(), originalIndex: null, deleted: false, edited: false }];
  }

  private isDirty(): boolean {
    return this.workingDataFolder !== this.baselineDataFolder
      || this.workingKeepLogFiles !== this.baselineKeepLogFiles
      || this.workingUsername !== this.baselineUsername
      || this.workingPassword !== ""
      || this.reputationLists.some((r) => r.deleted || r.edited || r.originalIndex === null);
  }

  private load() {
    this.loading = true;
    this.saving = false;
    this.error = undefined;
    ApiEndpoints.GeneralSettings.call({})
      .then((output) => {
        this.baselineDataFolder = output.dataFolder;
        this.workingDataFolder = output.dataFolder;
        this.baselineKeepLogFiles = output.keepLogFiles;
        this.workingKeepLogFiles = output.keepLogFiles;
        this.webServerEnabled = output.webServerEnabled;
        this.webServerHttpPort = output.webServerHttpPort;
        this.webServerAddress = output.webServerAddress || "";
        this.webServerCredentialsKey = output.webServerCredentialsKey || "";
        this.baselineUsername = output.webServerUsername || "";
        this.workingUsername = output.webServerUsername || "";
        this.workingPassword = "";
        this.reputationLists = output.reputationLists.map((list, index) => ({ list, originalIndex: index, deleted: false, edited: false }));
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
    if (!this.workingDataFolder.trim()) return "The data folder must not be blank.";
    if (this.workingKeepLogFiles < 0) return "Keep log files must be zero (disabled) or a positive number of days.";
    if (!this.workingUsername.trim()) return "The web login needs a username.";

    const seenIds = new Set<string>();
    for (const pending of this.reputationLists) {
      if (pending.deleted) continue;
      const list = pending.list;
      if (!list.id.trim()) return "A reputation list needs an id.";
      if (seenIds.has(list.id)) return "Reputation list id \"" + list.id + "\" is used more than once.";
      seenIds.add(list.id);
      if (!list.url.trim()) return "Reputation list \"" + list.id + "\" needs a URL.";
      if (list.refreshHours <= 0) return "Reputation list \"" + list.id + "\" needs a positive refresh interval.";
      if (list.score < 0 || list.score > 1) return "Reputation list \"" + list.id + "\" score must be between 0 and 1.";
    }
    return undefined;
  }

  private saveChanges() {
    const validationError = this.validate();
    if (validationError) {
      this.error = validationError;
      return;
    }

    this.saving = true;
    this.error = undefined;
    ApiEndpoints.UpdateGeneralSettings.call({
      dataFolder: this.workingDataFolder,
      keepLogFiles: this.workingKeepLogFiles,
      webServerUsername: this.workingUsername,
      webServerPassword: this.workingPassword,
      reputationLists: this.reputationLists.filter((r) => !r.deleted).map((r) => r.list),
    })
      .then(() => this.load())
      .catch((err: Error) => {
        this.saving = false;
        this.error = err.message;
        m.redraw();
      });
  }
}

function configRow(label: string, value: m.Children): m.Children {
  return m(".config-row", [m(".config-row-label", label), m(".config-row-value", value)]);
}

function actionRow(label: string, value: m.Children, action: m.Children): m.Children {
  return m(".config-row", [m(".config-row-label", label), m(".config-row-value.with-action", [m("span", value), action])]);
}

// TODO: replaced by the ChangeWebServerAddress/DisableWebServer calls.
function notImplementedYet() {
  Notifications.addNotification(new Notification(NotificationsClass.WEB_SERVER_CHANGE, NotificationsType.INFO, "Not implemented yet.", 4));
}
