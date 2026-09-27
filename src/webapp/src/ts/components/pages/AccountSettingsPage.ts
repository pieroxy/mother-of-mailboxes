import m from "mithril";
import { ApiEndpoints } from "../../auto/ApiEndpoints";
import {
  AccountBasicConfigDto,
  CredentialsInfoDto,
  LearningShortcutConfiguration,
  MailFilterRuleActionConfiguration,
  MailFilterRuleConfiguration,
  MailFilterRuleMatcherConfiguration,
  RuleType,
} from "../../auto/pieroxy-mom";
import { AbstractPage } from "./AbstractPage";
import { Routing } from "../../utils/navigation/Routing";
import { Endpoints } from "../../utils/navigation/Endpoints";
import { SettingsIcon } from "../atoms/icons/SettingsIcon";
import { ArrowCircleUpIcon } from "../atoms/icons/ArrowCircleUpIcon";
import { ArrowCircleDownIcon } from "../atoms/icons/ArrowCircleDownIcon";
import { DeleteIcon } from "../atoms/icons/DeleteIcon";

interface AccountSettingsPageAttrs {
  accountName: string;
}

/**
 * Read-only view of one account's whole configuration (everything config.json holds for it,
 * except the actual credentials — see AccountConfigApi/CredentialsInfoDto), in four sections:
 * Config, Credentials, Rules, Shortcuts. Editing/saving/restarting/deleting the account come
 * later — this is display only for now.
 */
export class AccountSettingsPage extends AbstractPage<AccountSettingsPageAttrs> {
  private accountName = "";
  private config: AccountBasicConfigDto | undefined;
  private credentials: CredentialsInfoDto | undefined;
  private rules: MailFilterRuleConfiguration[] = [];
  private savedRulesSnapshot: MailFilterRuleConfiguration[] = [];
  private deletedRules = new Set<MailFilterRuleConfiguration>();
  private rulesDirty = false;
  private savingRuleChanges = false;
  private shortcuts: LearningShortcutConfiguration[] = [];
  private loading = true;
  private error: string | undefined;

  getPageTitle(): string {
    return "MOM - Account settings";
  }

  oninit({ attrs }: m.Vnode<AccountSettingsPageAttrs>) {
    this.accountName = attrs.accountName;
    this.load();
  }

  render(): m.Children {
    return m("page.accountsettingspage", [
      m(".page-header", [
        m("a.page-back", { onclick: () => Routing.goToScreen(Endpoints.HOME) }, "‹ Back to accounts"),
        m("h1.page-title", this.accountName),
      ]),
      this.error ? m(".settings-error.errorMessage", this.error) : null,
      this.loading ? m(".page-loading", "Loading…") : this.renderContent(),
    ]);
  }

  private renderContent(): m.Children {
    if (!this.config || !this.credentials) return null;
    return m(".settings-content", [
      m(".page-card", [
        sectionHeader("Config", () => Routing.goToAccountConfigEdit(this.accountName)),
        renderConfigSection(this.config),
      ]),
      m(".page-card", [
        sectionHeader("Credentials", () => Routing.goToAccountCredentialsEdit(this.accountName)),
        renderCredentialsSection(this.credentials),
      ]),
      m(".page-card", [
        m(".section-header", [
          m("h2", "Rules"),
          this.rulesDirty ? m(".rule-changes-actions", [
            m("button.save-changes-button", { onclick: () => this.saveRuleChanges(), disabled: this.savingRuleChanges },
              this.savingRuleChanges ? "Saving…" : "Save Changes"),
            m("button.cancel-changes-button", { onclick: () => this.cancelRuleChanges(), disabled: this.savingRuleChanges }, "Cancel"),
          ]) : null,
        ]),
        this.renderRulesSection(),
      ]),
      m(".page-card", [sectionHeader("Shortcuts"), renderShortcutsSection(this.shortcuts)]),
    ]);
  }

  private renderRulesSection(): m.Children {
    if (this.rules.length === 0) return m(".settings-empty", "No rules configured.");
    return m(".rule-list", this.rules.map((rule, index) => this.renderRule(rule, index)));
  }

  private renderRule(rule: MailFilterRuleConfiguration, index: number): m.Children {
    const isDeleted = this.deletedRules.has(rule);
    const header = m(".rule-card-header", [
      m(".rule-index", rule.type === RuleType.LEARNED_RULES ? "#" + (index + 1)
        : "#" + (index + 1) + (rule.keepProcessing ? " · keeps processing" : "")),
      this.renderRuleControls(rule, index),
    ]);
    const body = rule.type === RuleType.LEARNED_RULES
      ? [m(".rule-learned-marker", "Learned rules run here")]
      : [
        m(".rule-section", [m(".rule-section-label", "When"), m("ul.tree-root", renderMatcherNode(rule.matcher))]),
        m(".rule-section", [m(".rule-section-label", "Then"), m("ul.tree-root", renderActionNode(rule.action))]),
      ];
    return m(".rule-card" + (isDeleted ? ".rule-deleted" : ""), { key: index }, [header, ...body]);
  }

  private renderRuleControls(rule: MailFilterRuleConfiguration, index: number): m.Children {
    const isDeleted = this.deletedRules.has(rule);
    const isFirst = index === 0;
    const isLast = index === this.rules.length - 1;
    return m(".rule-move-controls", [
      m("span.rule-move-button" + (isFirst || isDeleted ? ".disabled" : ""),
        { title: "Move up", onclick: () => this.moveRule(index, -1) }, m(ArrowCircleUpIcon)),
      m("span.rule-move-button" + (isLast || isDeleted ? ".disabled" : ""),
        { title: "Move down", onclick: () => this.moveRule(index, 1) }, m(ArrowCircleDownIcon)),
      m("span.rule-delete-button" + (isDeleted ? ".active" : ""),
        { title: isDeleted ? "Restore this rule" : "Delete this rule", onclick: () => this.toggleDeleteRule(rule) }, m(DeleteIcon)),
    ]);
  }

  private moveRule(index: number, delta: number) {
    if (this.deletedRules.has(this.rules[index])) return;
    const target = index + delta;
    if (target < 0 || target >= this.rules.length) return;
    const reordered = [...this.rules];
    [reordered[index], reordered[target]] = [reordered[target], reordered[index]];
    this.rules = reordered;
    this.rulesDirty = true;
  }

  private toggleDeleteRule(rule: MailFilterRuleConfiguration) {
    if (this.deletedRules.has(rule)) {
      this.deletedRules.delete(rule);
    } else {
      this.deletedRules.add(rule);
    }
    this.rulesDirty = true;
  }

  private cancelRuleChanges() {
    this.rules = [...this.savedRulesSnapshot];
    this.deletedRules.clear();
    this.rulesDirty = false;
  }

  private saveRuleChanges() {
    this.savingRuleChanges = true;
    this.error = undefined;
    const survivingRules = this.rules.filter((rule) => !this.deletedRules.has(rule));
    ApiEndpoints.UpdateAccountRules.call({ accountName: this.accountName, rules: survivingRules })
      .then(() => {
        this.savingRuleChanges = false;
        this.rulesDirty = false;
        this.rules = survivingRules;
        this.savedRulesSnapshot = [...survivingRules];
        this.deletedRules.clear();
        m.redraw();
      })
      .catch((err: Error) => {
        this.savingRuleChanges = false;
        this.error = err.message;
        m.redraw();
      });
  }

  private load() {
    this.loading = true;
    this.error = undefined;
    ApiEndpoints.AccountConfig.call({ accountName: this.accountName })
      .then((output) => {
        this.config = output.config;
        this.credentials = output.credentials;
        this.rules = output.rules;
        this.savedRulesSnapshot = [...output.rules];
        this.deletedRules.clear();
        this.rulesDirty = false;
        this.shortcuts = output.shortcuts;
        this.loading = false;
        m.redraw();
      })
      .catch((err: Error) => {
        this.loading = false;
        this.error = err.message;
        m.redraw();
      });
  }
}

function sectionHeader(title: string, onEdit?: () => void): m.Children {
  return m(".section-header", [
    m("h2", title),
    onEdit ? m("span.section-edit-link", { title: "Edit " + title.toLowerCase(), onclick: onEdit }, m(SettingsIcon)) : null,
  ]);
}

function configRow(label: string, value: m.Children): m.Children {
  return m(".config-row", [m(".config-row-label", label), m(".config-row-value", value)]);
}

function renderTokenList(items: string[]): m.Children {
  if (items.length === 0) return "—";
  return m(".token-list", items.map((item) => m(".token", { key: item }, item)));
}

function renderConfigSection(config: AccountBasicConfigDto): m.Children {
  return m(".config-grid", [
    configRow("Display name", config.displayName),
    configRow("Host", config.host),
    configRow("Port", String(config.port)),
    configRow("Run every", config.runEvery + "s"),
    configRow("Spam folder", config.classifierSpamFolderName || "Spam (default)"),
    configRow("Classifier excluded folders", renderTokenList(config.classifierExcludedFolders)),
    configRow("Classifier corpus retention", config.classifierCorpusRetentionDays > 0 ? config.classifierCorpusRetentionDays + " day(s)" : "disabled"),
    configRow("Classifier scan batch size", config.classifierCorpusScanBatchSize > 0 ? String(config.classifierCorpusScanBatchSize) : "default"),
    configRow("Discovery tree", config.discoveryTreeDisabled ? "disabled" : "enabled"),
  ]);
}

function renderCredentialsSection(credentials: CredentialsInfoDto): m.Children {
  return m(".config-grid", [
    configRow("Credentials key", credentials.credentialsKey),
    configRow("Username", credentials.username),
  ]);
}

function renderShortcutsSection(shortcuts: LearningShortcutConfiguration[]): m.Children {
  if (shortcuts.length === 0) return m(".settings-empty", "No learning shortcuts configured.");
  return m(".rule-list", shortcuts.map((shortcut) => m(".rule-card", { key: shortcut.name }, [
    m(".shortcut-name", shortcut.name),
    m(".rule-section", [m(".rule-section-label", "Matcher"), m("ul.tree-root", renderMatcherNode(shortcut.matcher))]),
    m(".rule-section", [m(".rule-section-label", "Action"), m("ul.tree-root", renderActionNode(shortcut.action))]),
  ])));
}

function renderMatcherNode(node: MailFilterRuleMatcherConfiguration): m.Children {
  const details: string[] = [];
  if (node.key) details.push(node.key);
  if (node.keys && node.keys.length > 0) details.push("{" + node.keys.join(", ") + "}");
  if (node.listIds && node.listIds.length > 0) details.push("lists: " + node.listIds.join(", "));
  return m("li.tree-node", [
    m(".tree-node-label", [
      m("span.tree-node-type", node.type),
      details.length > 0 ? m("span.tree-node-detail", ": " + details.join(", ")) : null,
    ]),
    node.children && node.children.length > 0 ? m("ul.tree-children", node.children.map(renderMatcherNode)) : null,
  ]);
}

function renderActionNode(node: MailFilterRuleActionConfiguration): m.Children {
  return m("li.tree-node", [
    m(".tree-node-label", [
      m("span.tree-node-type", node.type),
      node.key ? m("span.tree-node-detail", ": " + node.key) : null,
    ]),
    node.children && node.children.length > 0 ? m("ul.tree-children", node.children.map(renderActionNode)) : null,
  ]);
}
