import m from "mithril";
import { ApiEndpoints } from "../../auto/ApiEndpoints";
import { AccountBasicConfigDto, LearningShortcutConfiguration, RuleType } from "../../auto/pieroxy-mom";
import { AbstractPage } from "./AbstractPage";
import { Routing } from "../../utils/navigation/Routing";
import { Endpoints } from "../../utils/navigation/Endpoints";
import { SettingsIcon } from "../atoms/icons/SettingsIcon";
import { ArrowCircleUpIcon } from "../atoms/icons/ArrowCircleUpIcon";
import { ArrowCircleDownIcon } from "../atoms/icons/ArrowCircleDownIcon";
import { DeleteIcon } from "../atoms/icons/DeleteIcon";
import { renderActionNode, renderMatcherNode } from "../RuleTree";
import {
  AccountEditSession,
  PendingRule,
  accountEditSessions,
  isConfigFieldChanged,
  isRuleMoved,
  isSessionChanged,
} from "../../utils/AccountEditSession";

interface AccountSettingsPageAttrs {
  accountName: string;
}

/**
 * One account's whole configuration, in four sections: Config, Credentials, Rules, Shortcuts.
 * Nothing here calls the backend directly — every edit (Config/Credentials fields, rule reorder/
 * delete/edit/add) is staged locally in an AccountEditSession (see AccountEditSession.ts) and
 * shown highlighted (see the CSS's $value-changed) until "Save Changes" flushes the whole batch
 * in one call (UpdateAccountApi) and restarts the account once; "Discard Changes" just throws the
 * session away and reloads the real, unmodified state from the backend.
 */
export class AccountSettingsPage extends AbstractPage<AccountSettingsPageAttrs> {
  private accountName = "";
  private session: AccountEditSession | undefined;
  private savingChanges = false;
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
      this.loading || !this.session ? m(".page-loading", "Loading…") : this.renderContent(this.session),
    ]);
  }

  private renderContent(session: AccountEditSession): m.Children {
    return m(".settings-content", [
      isSessionChanged(session) ? this.renderChangesBar() : null,
      m(".page-card", [
        sectionHeader("Config", () => Routing.goToAccountConfigEdit(this.accountName)),
        renderConfigSection(session),
      ]),
      m(".page-card", [
        sectionHeader("Credentials", () => Routing.goToAccountCredentialsEdit(this.accountName)),
        renderCredentialsSection(session),
      ]),
      m(".page-card", [
        sectionHeader("Rules"),
        this.renderRulesSection(session),
        m("button.add-rule-button", { onclick: () => Routing.goToRuleEdit(this.accountName, "new") }, "+ Add rule"),
      ]),
      m(".page-card", [sectionHeader("Shortcuts"), renderShortcutsSection(session.shortcuts)]),
    ]);
  }

  private renderChangesBar(): m.Children {
    return m(".settings-changes-bar", [
      m("span", "You have unsaved changes."),
      m(".changes-actions", [
        m("button.save-changes-button", { onclick: () => this.saveChanges(), disabled: this.savingChanges },
          this.savingChanges ? "Saving…" : "Save Changes"),
        m("button.discard-changes-button", { onclick: () => this.discardChanges(), disabled: this.savingChanges }, "Discard Changes"),
      ]),
    ]);
  }

  private renderRulesSection(session: AccountEditSession): m.Children {
    if (session.rules.length === 0) return m(".settings-empty", "No rules configured.");
    return m(".rule-list", session.rules.map((pendingRule, index) => this.renderRule(session, pendingRule, index)));
  }

  private renderRule(session: AccountEditSession, pendingRule: PendingRule, index: number): m.Children {
    const rule = pendingRule.rule;
    const isDeleted = pendingRule.deleted;
    const header = m(".rule-card-header", [
      m(".rule-card-header-left", [
        m("span.rule-edit-link" + (isDeleted ? ".disabled" : ""),
          { title: "Edit this rule", onclick: () => Routing.goToRuleEdit(this.accountName, index) }, m(SettingsIcon)),
        m(".rule-index", rule.type === RuleType.LEARNED_RULES ? "#" + (index + 1)
          : "#" + (index + 1) + (rule.keepProcessing ? " · keeps processing" : "")),
        this.renderRuleTags(session, pendingRule),
      ]),
      this.renderRuleControls(session, pendingRule, index),
    ]);
    const body = rule.type === RuleType.LEARNED_RULES
      ? [m(".rule-learned-marker", "Learned rules run here")]
      : [
        m(".rule-section", [m(".rule-section-label", "When"), m("ul.tree-root", renderMatcherNode(rule.matcher))]),
        m(".rule-section", [m(".rule-section-label", "Then"), m("ul.tree-root", renderActionNode(rule.action))]),
      ];
    return m(".rule-card" + (isDeleted ? ".rule-deleted" : ""), { key: index }, [header, ...body]);
  }

  private renderRuleTags(session: AccountEditSession, pendingRule: PendingRule): m.Children {
    const tags: m.Children[] = [];
    if (pendingRule.originalIndex === null) tags.push(m("span.rule-tag", { key: "new" }, "New"));
    else if (isRuleMoved(session, pendingRule)) tags.push(m("span.rule-tag", { key: "moved" }, "Moved"));
    if (pendingRule.edited) tags.push(m("span.rule-tag", { key: "edited" }, "Edited"));
    return tags.length > 0 ? m(".rule-tags", tags) : null;
  }

  private renderRuleControls(session: AccountEditSession, pendingRule: PendingRule, index: number): m.Children {
    const isDeleted = pendingRule.deleted;
    const isFirst = index === 0;
    const isLast = index === session.rules.length - 1;
    return m(".rule-move-controls", [
      m("span.rule-move-button" + (isFirst || isDeleted ? ".disabled" : ""),
        { title: "Move up", onclick: () => this.moveRule(session, index, -1) }, m(ArrowCircleUpIcon)),
      m("span.rule-move-button" + (isLast || isDeleted ? ".disabled" : ""),
        { title: "Move down", onclick: () => this.moveRule(session, index, 1) }, m(ArrowCircleDownIcon)),
      m("span.rule-delete-button" + (isDeleted ? ".active" : ""),
        { title: isDeleted ? "Restore this rule" : "Delete this rule", onclick: () => this.toggleDeleteRule(pendingRule) }, m(DeleteIcon)),
    ]);
  }

  private moveRule(session: AccountEditSession, index: number, delta: number) {
    if (session.rules[index].deleted) return;
    const target = index + delta;
    if (target < 0 || target >= session.rules.length) return;
    const reordered = [...session.rules];
    [reordered[index], reordered[target]] = [reordered[target], reordered[index]];
    session.rules = reordered;
  }

  private toggleDeleteRule(pendingRule: PendingRule) {
    pendingRule.deleted = !pendingRule.deleted;
  }

  private saveChanges() {
    const session = this.session;
    if (!session) return;
    this.savingChanges = true;
    this.error = undefined;
    ApiEndpoints.UpdateAccount.call({
      accountName: this.accountName,
      host: session.workingConfig.host,
      port: session.workingConfig.port,
      runEvery: session.workingConfig.runEvery,
      classifierSpamFolderName: session.workingConfig.classifierSpamFolderName,
      classifierExcludedFolders: session.workingConfig.classifierExcludedFolders,
      classifierCorpusRetentionDays: session.workingConfig.classifierCorpusRetentionDays,
      classifierCorpusScanBatchSize: session.workingConfig.classifierCorpusScanBatchSize,
      discoveryTreeDisabled: session.workingConfig.discoveryTreeDisabled,
      username: session.workingUsername,
      password: session.workingPassword,
      rules: session.rules.filter((pendingRule) => !pendingRule.deleted).map((pendingRule) => pendingRule.rule),
    })
      .then(() => {
        this.savingChanges = false;
        accountEditSessions.discard(this.accountName);
        this.load();
      })
      .catch((err: Error) => {
        this.savingChanges = false;
        this.error = err.message;
        m.redraw();
      });
  }

  private discardChanges() {
    accountEditSessions.discard(this.accountName);
    this.load();
  }

  private load() {
    this.loading = true;
    this.error = undefined;
    accountEditSessions.load(this.accountName)
      .then((session) => {
        this.session = session;
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

function configRow(label: string, value: m.Children, changed: boolean): m.Children {
  return m(".config-row", [m(".config-row-label", label), m(".config-row-value" + (changed ? ".value-changed" : ""), value)]);
}

function renderTokenList(items: string[]): m.Children {
  if (items.length === 0) return "—";
  return m(".token-list", items.map((item) => m(".token", { key: item }, item)));
}

function renderConfigSection(session: AccountEditSession): m.Children {
  const config: AccountBasicConfigDto = session.workingConfig;
  const changed = (field: keyof AccountBasicConfigDto) => isConfigFieldChanged(session, field);
  return m(".config-grid", [
    configRow("Display name", config.displayName, false),
    configRow("Host", config.host, changed("host")),
    configRow("Port", String(config.port), changed("port")),
    configRow("Run every", config.runEvery + "s", changed("runEvery")),
    configRow("Spam folder", config.classifierSpamFolderName || "Spam (default)", changed("classifierSpamFolderName")),
    configRow("Classifier excluded folders", renderTokenList(config.classifierExcludedFolders), changed("classifierExcludedFolders")),
    configRow("Classifier corpus retention", config.classifierCorpusRetentionDays > 0 ? config.classifierCorpusRetentionDays + " day(s)" : "disabled", changed("classifierCorpusRetentionDays")),
    configRow("Classifier scan batch size", config.classifierCorpusScanBatchSize > 0 ? String(config.classifierCorpusScanBatchSize) : "default", changed("classifierCorpusScanBatchSize")),
    configRow("Discovery tree", config.discoveryTreeDisabled ? "disabled" : "enabled", changed("discoveryTreeDisabled")),
  ]);
}

function renderCredentialsSection(session: AccountEditSession): m.Children {
  return m(".config-grid", [
    configRow("Credentials key", session.credentialsKey, false),
    configRow("Username", session.workingUsername, session.workingUsername !== session.baselineUsername),
    configRow("Password", session.workingPassword !== "" ? "(will be changed)" : "(unchanged)", session.workingPassword !== ""),
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
