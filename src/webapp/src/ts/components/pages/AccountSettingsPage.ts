import m from "mithril";
import { ApiEndpoints } from "../../auto/ApiEndpoints";
import { AccountBasicConfigDto, MailFilterRuleMatcherConfiguration, RuleType } from "../../auto/pieroxy-mom";
import { AbstractPage } from "./AbstractPage";
import { Routing } from "../../utils/navigation/Routing";
import { Endpoints } from "../../utils/navigation/Endpoints";
import { SettingsIcon } from "../atoms/icons/SettingsIcon";
import { ArrowCircleUpIcon } from "../atoms/icons/ArrowCircleUpIcon";
import { ArrowCircleDownIcon } from "../atoms/icons/ArrowCircleDownIcon";
import { DeleteIcon } from "../atoms/icons/DeleteIcon";
import { renderActionNode, renderMatcherNode } from "../RuleTree";
import { TokenListEditor } from "../TokenListEditor";
import { Dialogs } from "../../utils/Dialogs";
import {
  AccountEditSession,
  PendingLearnedRule,
  PendingRule,
  PendingShortcut,
  accountEditSessions,
  finalizeLearnedRule,
  isConfigChanged,
  isConfigFieldChanged,
  isCredentialsChanged,
  isLearnedRulesChanged,
  isRuleMoved,
  isRulesChanged,
  isSessionChanged,
  isShortcutsChanged,
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
  private deleting = false;

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
        m("button.add-item-button", { onclick: () => Routing.goToRuleEdit(this.accountName, "new") }, "+ Add rule"),
      ]),
      m(".page-card", [
        sectionHeader("Shortcuts"),
        this.renderShortcutsSection(session),
        m("button.add-item-button", { onclick: () => Routing.goToShortcutEdit(this.accountName, "new") }, "+ Add shortcut"),
      ]),
      m(".page-card", [
        sectionHeader("Learned Rules"),
        this.renderLearnedRulesSection(session),
      ]),
      m(".page-card.danger-zone", [
        m("h2", "Danger Zone"),
        m("p.field-hint", "Deleting this account stops it and removes it from config.json. This cannot be undone."),
        m("button.danger-button", { onclick: () => this.confirmDeleteAccount(), disabled: this.deleting },
          this.deleting ? "Deleting…" : "Delete Account"),
      ]),
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
      m("span.rule-edit-link" + (isDeleted ? ".disabled" : ""),
        { title: "Edit this rule", onclick: () => Routing.goToRuleEdit(this.accountName, index) }, m(SettingsIcon)),
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

  private renderShortcutsSection(session: AccountEditSession): m.Children {
    if (session.shortcuts.length === 0) return m(".settings-empty", "No learning shortcuts configured.");
    return m(".rule-list", session.shortcuts.map((pendingShortcut, index) => this.renderShortcut(pendingShortcut, index)));
  }

  private renderShortcut(pendingShortcut: PendingShortcut, index: number): m.Children {
    const shortcut = pendingShortcut.shortcut;
    const isDeleted = pendingShortcut.deleted;
    const header = m(".rule-card-header", [
      m(".rule-card-header-left", [
        m(".shortcut-name", shortcut.name),
        this.renderShortcutTags(pendingShortcut),
      ]),
      m(".rule-move-controls", [
        m("span.rule-edit-link" + (isDeleted ? ".disabled" : ""),
          { title: "Edit this shortcut", onclick: () => Routing.goToShortcutEdit(this.accountName, index) }, m(SettingsIcon)),
        m("span.rule-delete-button" + (isDeleted ? ".active" : ""),
          { title: isDeleted ? "Restore this shortcut" : "Delete this shortcut", onclick: () => this.toggleDeleteShortcut(pendingShortcut) }, m(DeleteIcon)),
      ]),
    ]);
    const body = [
      m(".rule-section", [m(".rule-section-label", "Matcher"), m("ul.tree-root", renderMatcherNode(shortcut.matcher))]),
      m(".rule-section", [m(".rule-section-label", "Action"), m("ul.tree-root", renderActionNode(shortcut.action))]),
    ];
    return m(".rule-card" + (isDeleted ? ".rule-deleted" : ""), { key: index }, [header, ...body]);
  }

  private renderShortcutTags(pendingShortcut: PendingShortcut): m.Children {
    const tags: m.Children[] = [];
    if (pendingShortcut.originalIndex === null) tags.push(m("span.rule-tag", { key: "new" }, "New"));
    if (pendingShortcut.edited) tags.push(m("span.rule-tag", { key: "edited" }, "Edited"));
    return tags.length > 0 ? m(".rule-tags", tags) : null;
  }

  private toggleDeleteShortcut(pendingShortcut: PendingShortcut) {
    pendingShortcut.deleted = !pendingShortcut.deleted;
  }

  private renderLearnedRulesSection(session: AccountEditSession): m.Children {
    if (session.learnedRules.length === 0) {
      return m(".settings-empty", "No rules learned yet — drop an example message into mom-rules/ to create one.");
    }
    return m(".rule-list", session.learnedRules.map((pending, index) => this.renderLearnedRule(pending, index)));
  }

  private renderLearnedRule(pending: PendingLearnedRule, index: number): m.Children {
    const rule = pending.rule;
    const isDeleted = pending.deleted;
    const keys = rule.matcher.keys && rule.matcher.keys.length > 0 ? rule.matcher.keys : (rule.matcher.key ? [rule.matcher.key] : []);
    const header = m(".rule-card-header", [
      m(".rule-card-header-left", [
        m(".rule-index", rule.matcher.type),
        this.renderLearnedRuleTags(pending),
      ]),
      m(".rule-move-controls", [
        m("span.rule-delete-button" + (isDeleted ? ".active" : ""),
          { title: isDeleted ? "Restore this learned rule" : "Delete this learned rule", onclick: () => this.toggleDeleteLearnedRule(pending) }, m(DeleteIcon)),
      ]),
    ]);
    const body = [
      m(".rule-section", [
        m(".rule-section-label", "Keys"),
        m(TokenListEditor, {
          tokens: keys,
          onChange: (updatedKeys) => this.updateLearnedRuleKeys(pending, updatedKeys),
          placeholder: "Add a value",
          disabled: isDeleted,
        }),
      ]),
      m(".rule-section", [m(".rule-section-label", "Action"), m("ul.tree-root", renderActionNode(rule.action))]),
    ];
    return m(".rule-card" + (isDeleted ? ".rule-deleted" : ""), { key: index }, [header, ...body]);
  }

  private renderLearnedRuleTags(pending: PendingLearnedRule): m.Children {
    return pending.edited ? m(".rule-tags", m("span.rule-tag", "Edited")) : null;
  }

  private toggleDeleteLearnedRule(pending: PendingLearnedRule) {
    pending.deleted = !pending.deleted;
  }

  private updateLearnedRuleKeys(pending: PendingLearnedRule, keys: string[]) {
    const matcher = { type: pending.rule.matcher.type, keys } as MailFilterRuleMatcherConfiguration;
    pending.rule = { ...pending.rule, matcher };
    pending.edited = true;
  }

  private saveChanges() {
    const session = this.session;
    if (!session) return;

    const survivingLearnedRules = session.learnedRules.filter((pending) => !pending.deleted);
    if (survivingLearnedRules.some((pending) => (pending.rule.matcher.keys || []).length === 0 && !pending.rule.matcher.key)) {
      this.error = "A learned rule needs at least one key — remove it instead if it should no longer apply to anything.";
      return;
    }

    this.savingChanges = true;
    this.error = undefined;

    // Two independent calls: UpdateAccount restarts the account (needed for config/credentials/
    // rules/shortcuts), while learned rules apply immediately with no restart — see
    // ServiceProvider#updateLearnedRules. Each only fires if its own part of the session actually
    // changed, so e.g. fixing a single learned-rule key never forces an unnecessary IMAP reconnect.
    const calls: Promise<unknown>[] = [];
    if (isConfigChanged(session) || isCredentialsChanged(session) || isRulesChanged(session) || isShortcutsChanged(session)) {
      calls.push(ApiEndpoints.UpdateAccount.call({
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
        shortcuts: session.shortcuts.filter((pendingShortcut) => !pendingShortcut.deleted).map((pendingShortcut) => pendingShortcut.shortcut),
      }));
    }
    if (isLearnedRulesChanged(session)) {
      calls.push(ApiEndpoints.UpdateLearnedRules.call({
        accountName: this.accountName,
        rules: survivingLearnedRules.map((pending) => finalizeLearnedRule(pending.rule)),
      }));
    }

    Promise.all(calls)
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

  private confirmDeleteAccount() {
    Dialogs.confirm(
      "Delete account \"" + this.accountName + "\"? It will stop running and be removed from config.json. There's no coming back from this.",
      "Delete Account", "Cancel",
      () => this.deleteAccount(),
    );
  }

  private deleteAccount() {
    this.deleting = true;
    this.error = undefined;
    ApiEndpoints.DeleteAccount.call({ accountName: this.accountName })
      .then(() => {
        accountEditSessions.discard(this.accountName);
        Routing.goToScreen(Endpoints.HOME);
      })
      .catch((err: Error) => {
        this.deleting = false;
        this.error = err.message;
        m.redraw();
      });
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
