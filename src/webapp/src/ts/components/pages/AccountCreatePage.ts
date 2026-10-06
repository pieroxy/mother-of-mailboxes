import m from "mithril";
import { AbstractPage } from "./AbstractPage";
import { ApiEndpoints } from "../../auto/ApiEndpoints";
import { MailFilterRuleConfiguration } from "../../auto/pieroxy-mom";
import { Endpoints } from "../../utils/navigation/Endpoints";
import { Routing } from "../../utils/navigation/Routing";
import { TestImapConnectionButton } from "../TestImapConnectionButton";
import { renderActionNode, renderMatcherNode } from "../RuleTree";

const STEPS = ["Name", "IMAP connection", "Spam folder", "Spam handling", "Schedule", "Review"];
const FOLDER_DATALIST_ID = "account-create-folders";
// Same subset of FileNameValidator's rules as can be checked without a round trip; the server
// re-validates the name in full.
const FORBIDDEN_NAME_CHARS = /[<>:"/\\|?*\u0000-\u001f]/;

/**
 * Account creation wizard. Nothing is persisted until the last step's "Create account", which
 * sends everything to CreateAccountApi in one call; the spam rules themselves are generated
 * server-side (DefaultSpamRules), the review step only previews them.
 */
export class AccountCreatePage extends AbstractPage {
  private step = 0;
  private existingNames: string[] = [];
  private folders: string[] = [];
  private spamRulesPreview: MailFilterRuleConfiguration[] = [];
  private error: string | undefined;
  private creating = false;

  private displayName = "";
  private host = "";
  private port = 993;
  private username = "";
  private password = "";
  // The connection settings last tested successfully — any edit to them requires a new test.
  private testedConnection: string | undefined;
  private spamFolder = "";
  private handleSpam = true;
  private classifierFolder = "SpamML";
  private classifierCorpusRetentionDays = 90;
  private runEvery = 300;
  private connectTimeout = 0;
  private readTimeout = 0;

  getPageTitle(): string {
    return "MOM - New account";
  }

  oninit() {
    ApiEndpoints.Accounts.call({})
      .then((output) => (this.existingNames = output.accounts.map((account) => account.name)))
      .catch(() => undefined);
  }

  render(): m.Children {
    return m("page.accountcreatepage", [
      m(".page-header", [
        m("a.page-back", { onclick: () => Routing.goToScreen(Endpoints.HOME) }, "‹ Back"),
        m("h1.page-title", "New account"),
      ]),
      m(".wizard-steps", STEPS.map((title, index) =>
        m(".wizard-step" + (index === this.step ? ".current" : index < this.step ? ".done" : ""), (index + 1) + ". " + title))),
      this.error ? m(".settings-error.errorMessage", this.error) : null,
      m(".page-card.edit-form", [
        this.renderStep(),
        this.renderActions(),
      ]),
    ]);
  }

  private renderStep(): m.Children {
    switch (this.step) {
      case 0: return this.renderNameStep();
      case 1: return this.renderConnectionStep();
      case 2: return this.renderSpamFolderStep();
      case 3: return this.renderSpamHandlingStep();
      case 4: return this.renderScheduleStep();
      default: return this.renderReviewStep();
    }
  }

  private renderNameStep(): m.Children {
    return this.field("Account name", m("input", {
      type: "text", value: this.displayName,
      oninput: (e: Event) => (this.displayName = (e.target as HTMLInputElement).value),
    }), "Names this account's files in the data folder: it can't be changed later.");
  }

  private renderConnectionStep(): m.Children {
    return [
      this.field("Host", m("input", {
        type: "text", value: this.host,
        oninput: (e: Event) => (this.host = (e.target as HTMLInputElement).value),
      })),
      this.field("Port", m("input", {
        type: "number", value: this.port,
        oninput: (e: Event) => (this.port = Number((e.target as HTMLInputElement).value)),
      }), "IMAPS (implicit TLS) only, usually 993."),
      this.field("Username", m("input", {
        type: "text", value: this.username,
        oninput: (e: Event) => (this.username = (e.target as HTMLInputElement).value),
      })),
      this.field("Password", m("input", {
        type: "password", value: this.password,
        oninput: (e: Event) => (this.password = (e.target as HTMLInputElement).value),
      })),
      m(TestImapConnectionButton, {
        accountName: "", host: this.host, port: this.port, connectTimeout: this.connectTimeout, readTimeout: this.readTimeout,
        username: this.username, password: this.password,
        onResult: (connected) => (this.testedConnection = connected ? this.connectionKey() : undefined),
      }),
    ];
  }

  private renderSpamFolderStep(): m.Children {
    return this.field("Spam folder", [
      m("input", {
        type: "text", value: this.spamFolder, list: FOLDER_DATALIST_ID, placeholder: "Spam (default)",
        oninput: (e: Event) => (this.spamFolder = (e.target as HTMLInputElement).value),
      }),
      m("datalist#" + FOLDER_DATALIST_ID, this.folders.map((folder) => m("option", { key: folder, value: folder }))),
    ], "Where spam goes, and what the classifier learns spam from (everything else but INBOX is legitimate mail). Created if missing.");
  }

  private renderSpamHandlingStep(): m.Children {
    return [
      this.field("Do you want MOM to handle spam for you?", m(".radio-group", [
        this.radio("Yes, create the recommended spam rules", true),
        this.radio("No, I'll add rules myself", false),
      ])),
      this.handleSpam ? [
        m(".wizard-notice", [
          m("p", "Mail flagged by the subject classifier goes to its own folder below, kept out of the classifier's training data "
            + "so it never learns from its own verdicts. The classifier stays inactive until it has seen at least 50 spam and 50 legitimate messages."),
          m("p", "MOM learns what legitimate mail looks like from your folders, never from INBOX: file or archive the mail you keep "
            + "(inbox zero), or the classifier has nothing to learn from."),
          m("p", "Reputation rules are only created for the reputation lists configured in the general settings."),
        ]),
        this.field("Classifier folder", m("input", {
          type: "text", value: this.classifierFolder, list: FOLDER_DATALIST_ID,
          oninput: (e: Event) => (this.classifierFolder = (e.target as HTMLInputElement).value),
        })),
        m("datalist#" + FOLDER_DATALIST_ID, this.folders.map((folder) => m("option", { key: folder, value: folder }))),
        this.field("Classifier corpus retention (days)", m("input", {
          type: "number", value: this.classifierCorpusRetentionDays,
          oninput: (e: Event) => (this.classifierCorpusRetentionDays = Number((e.target as HTMLInputElement).value)),
        }), "How long collected messages are kept to train the classifier."),
      ] : m(".wizard-notice", m("p", "No rules will be created: MOM won't touch your mail until you add some from this account's settings page.")),
    ];
  }

  private renderScheduleStep(): m.Children {
    return [
      this.field("Run every (seconds)", m("input", {
        type: "number", value: this.runEvery,
        oninput: (e: Event) => (this.runEvery = Number((e.target as HTMLInputElement).value)),
      }), "New mail is usually picked up right away (IMAP IDLE): this is the longest it can wait otherwise."),
      this.field("Connect timeout (seconds, 0 = default 5)", m("input", {
        type: "number", value: this.connectTimeout,
        oninput: (e: Event) => (this.connectTimeout = Number((e.target as HTMLInputElement).value)),
      })),
      this.field("Read timeout (seconds, 0 = default 60)", m("input", {
        type: "number", value: this.readTimeout,
        oninput: (e: Event) => (this.readTimeout = Number((e.target as HTMLInputElement).value)),
      })),
    ];
  }

  private renderReviewStep(): m.Children {
    return [
      m(".config-grid", [
        this.reviewRow("Name", this.displayName.trim()),
        this.reviewRow("Server", this.host.trim() + ":" + this.port),
        this.reviewRow("Username", this.username.trim()),
        this.reviewRow("Spam folder", this.spamFolder.trim() || "Spam"),
        this.reviewRow("Spam handling", this.handleSpam
          ? "Yes — classifier folder " + this.classifierFolder.trim() + ", corpus kept " + this.classifierCorpusRetentionDays + " day(s)"
          : "No"),
        this.reviewRow("Run every", this.runEvery + "s"),
        this.reviewRow("Timeouts", "connect " + (this.connectTimeout || 5) + "s, read " + (this.readTimeout || 60) + "s"),
      ]),
      this.handleSpam ? [
        m("h2", "Rules"),
        m(".rule-list", this.spamRulesPreview.map((rule, index) => m(".rule-card", { key: index }, [
          m(".rule-section", [m(".rule-section-label", "When"), m("ul.tree-root", renderMatcherNode(rule.matcher))]),
          m(".rule-section", [m(".rule-section-label", "Then"), m("ul.tree-root", renderActionNode(rule.action))]),
        ]))),
      ] : null,
    ];
  }

  private renderActions(): m.Children {
    const last = this.step === STEPS.length - 1;
    return m(".edit-actions", [
      this.step > 0 ? m("button.cancel-button", { onclick: () => this.goTo(this.step - 1) }, "Back") : null,
      last
        ? m("button.ok-button", { onclick: () => this.create(), disabled: this.creating }, this.creating ? "Creating…" : "Create account")
        : m("button.ok-button", { onclick: () => this.next() }, "Next"),
      m("button.cancel-button", { onclick: () => Routing.goToScreen(Endpoints.HOME) }, "Cancel"),
    ]);
  }

  private radio(label: string, value: boolean): m.Children {
    return m("label.checkbox-field", [
      m("input", { type: "radio", name: "handle-spam", checked: this.handleSpam === value, onchange: () => (this.handleSpam = value) }),
      label,
    ]);
  }

  private field(label: string, control: m.Children, hint?: string): m.Children {
    return m(".edit-field", [m("label", label), control, hint ? m("span.field-hint", hint) : null]);
  }

  private reviewRow(label: string, value: string): m.Children {
    return m(".config-row", [m(".config-row-label", label), m(".config-row-value", value)]);
  }

  private connectionKey(): string {
    return JSON.stringify([this.host.trim(), this.port, this.username.trim(), this.password]);
  }

  /** Mirrors CreateAccountApi's checks, per step, so mistakes surface where they're made. */
  private validateStep(): string | undefined {
    switch (this.step) {
      case 0: {
        const name = this.displayName.trim();
        if (!name) return "The account name must not be blank.";
        if (FORBIDDEN_NAME_CHARS.test(name)) return "The account name must not contain any of < > : \" / \\ | ? *";
        if (this.existingNames.some((existing) => existing.toLowerCase() === name.toLowerCase())) return "An account named \"" + name + "\" already exists.";
        return undefined;
      }
      case 1:
        if (!this.host.trim()) return "Host must not be blank.";
        if (this.port <= 0 || this.port > 65535) return "Port must be between 1 and 65535.";
        if (!this.username.trim() || !this.password) return "Username and password must not be blank.";
        if (this.testedConnection !== this.connectionKey()) return "Test the connection successfully before going further.";
        return undefined;
      case 3:
        if (!this.handleSpam) return undefined;
        if (!this.classifierFolder.trim()) return "The classifier folder must not be blank.";
        if (this.classifierFolder.trim() === (this.spamFolder.trim() || "Spam")) return "The classifier folder must differ from the spam folder.";
        if (this.classifierCorpusRetentionDays <= 0) return "The classifier corpus retention must be a positive number of days.";
        return undefined;
      case 4:
        if (this.runEvery <= 0) return "\"Run every\" must be a positive number of seconds.";
        if (this.connectTimeout < 0 || this.readTimeout < 0) return "Timeouts must not be negative.";
        return undefined;
      default:
        return undefined;
    }
  }

  private next() {
    const validationError = this.validateStep();
    if (validationError) {
      this.error = validationError;
      return;
    }
    if (this.step === 1) this.loadFolders();
    if (this.step === STEPS.length - 2 && this.handleSpam) this.loadSpamRulesPreview();
    this.goTo(this.step + 1);
  }

  private goTo(step: number) {
    this.error = undefined;
    this.step = step;
  }

  /** Only offers suggestions: on failure, folder fields stay plain free text. */
  private loadFolders() {
    ApiEndpoints.ImapFolders.call({
      host: this.host.trim(), port: this.port, connectTimeout: this.connectTimeout, readTimeout: this.readTimeout,
      username: this.username.trim(), password: this.password,
    })
      .then((output) => {
        this.folders = output.folders;
        m.redraw();
      })
      .catch(() => undefined);
  }

  private loadSpamRulesPreview() {
    this.spamRulesPreview = [];
    ApiEndpoints.DefaultSpamRules.call({ spamFolder: this.spamFolder.trim(), classifierFolder: this.classifierFolder.trim() })
      .then((output) => {
        this.spamRulesPreview = output.rules;
        m.redraw();
      })
      .catch((err: Error) => {
        this.error = err.message;
        m.redraw();
      });
  }

  private create() {
    this.creating = true;
    this.error = undefined;
    const displayName = this.displayName.trim();
    ApiEndpoints.CreateAccount.call({
      displayName,
      host: this.host.trim(),
      port: this.port,
      username: this.username.trim(),
      password: this.password,
      runEvery: this.runEvery,
      connectTimeout: this.connectTimeout,
      readTimeout: this.readTimeout,
      classifierSpamFolderName: this.spamFolder.trim(),
      handleSpam: this.handleSpam,
      classifierFolderName: this.classifierFolder.trim(),
      classifierCorpusRetentionDays: this.classifierCorpusRetentionDays,
    })
      .then(() => Routing.goToAccountSettings(displayName))
      .catch((err: Error) => {
        this.creating = false;
        this.error = err.message;
        m.redraw();
      });
  }
}
