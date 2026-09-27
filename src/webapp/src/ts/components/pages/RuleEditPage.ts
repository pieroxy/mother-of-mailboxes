import m from "mithril";
import {
  ActionType,
  MailFilterRuleActionConfiguration,
  MailFilterRuleConfiguration,
  MailFilterRuleMatcherConfiguration,
  ReputationListDto,
  RuleType,
} from "../../auto/pieroxy-mom";
import { ApiEndpoints } from "../../auto/ApiEndpoints";
import { AbstractPage } from "./AbstractPage";
import { Routing } from "../../utils/navigation/Routing";
import { renderActionNode } from "../RuleTree";
import { AccountEditSession, PendingRule, accountEditSessions } from "../../utils/AccountEditSession";
import { MatcherNodeEditor, defaultMatcherNode, finalizeMatcherNode, validateMatcherNode } from "../MatcherNodeEditor";

interface RuleEditPageAttrs {
  accountName: string;
  /** "new" to append a rule, or the rule's position in the session's rules list (as a string, straight from the route) to edit it. */
  ruleIndex: string;
}

interface ActionTypeOption {
  value: ActionType;
  label: string;
  needsKey: boolean;
  /** What the key field means for this action — shown as its label, since "key" means something different per type (a folder vs. a log message). */
  keyLabel?: string;
  keyPlaceholder?: string;
  /** The key is an existing (or not-yet-created) IMAP folder name — offered as an editable dropdown (a text input backed by a datalist of the account's real folders, see AccountFoldersApi) instead of plain free text. */
  isFolderName?: boolean;
}

// AND/OR excluded: editing a composite action isn't supported here yet (see COMPOSITE_ACTION_TYPES
// below) — only matchers can be composites now, see MatcherNodeEditor.
const ACTION_TYPE_OPTIONS: ActionTypeOption[] = [
  { value: ActionType.MOVE_TO, label: "Move to folder", needsKey: true, keyLabel: "Destination folder", isFolderName: true },
  { value: ActionType.MOVE_TO_AND_READ, label: "Move to folder and mark as read", needsKey: true, keyLabel: "Destination folder", isFolderName: true },
  { value: ActionType.READ, label: "Mark as read", needsKey: false },
  {
    value: ActionType.NOOP, label: "Do nothing (log only)", needsKey: true,
    keyLabel: "Log message", keyPlaceholder: "What to log when this rule matches",
  },
];

const FOLDER_DATALIST_ID = "destination-folder-options";

const COMPOSITE_ACTION_TYPES: Set<string> = new Set([ActionType.AND, ActionType.OR]);

/**
 * Creates or edits one rule — reused for both, per its own route: "new" appends a rule to the
 * account's list, an index edits the rule already there. The matcher side ("When") is a fully
 * editable tree, including AND/OR/NOT composites — see MatcherNodeEditor. The action side
 * ("Then") is still leaf-only; a rule whose action is a composite (AND/OR) shows read-only (see
 * RuleTree) and OK leaves that side untouched. Nothing is sent to the backend here: "OK" just
 * writes the edited rule into the account's AccountEditSession and returns to the settings page,
 * where it shows tagged "New"/"Edited" until "Save Changes" persists the whole batch in one call
 * (see AccountSettingsPage/UpdateAccountApi).
 */
export class RuleEditPage extends AbstractPage<RuleEditPageAttrs> {
  private accountName = "";
  private ruleIndex: number | null = null; // null = creating a new rule
  private session: AccountEditSession | undefined;
  private isLearnedRulesMarker = false;

  private keepProcessing = false;

  private matcherRoot: MailFilterRuleMatcherConfiguration = defaultMatcherNode();
  private reputationLists: ReputationListDto[] = [];

  private actionType: ActionType = ActionType.MOVE_TO;
  private actionKey = "";
  private actionIsComposite = false;
  private originalAction: MailFilterRuleActionConfiguration | undefined;
  private accountFolders: string[] = [];

  private loading = true;
  private error: string | undefined;

  getPageTitle(): string {
    return "MOM - Edit rule";
  }

  oninit({ attrs }: m.Vnode<RuleEditPageAttrs>) {
    this.accountName = attrs.accountName;
    this.ruleIndex = attrs.ruleIndex === "new" ? null : Number(attrs.ruleIndex);
    this.load();
  }

  render(): m.Children {
    const title = (this.ruleIndex === null ? "New rule — " : "Edit rule — ") + this.accountName;
    return m("page.ruleeditpage", [
      m(".page-header", [
        m("a.page-back", { onclick: () => this.cancel() }, "‹ Back to settings"),
        m("h1.page-title", title),
      ]),
      this.error ? m(".settings-error.errorMessage", this.error) : null,
      this.loading ? m(".page-loading", "Loading…") : this.renderForm(),
    ]);
  }

  private renderForm(): m.Children {
    if (this.isLearnedRulesMarker) {
      return m(".page-card.edit-form", [
        m(".rule-learned-marker", "This is the account's \"learned rules\" marker — it has no matcher or action of its own."),
        this.field("Keep processing after this", this.renderKeepProcessingCheckbox()),
        this.renderActions(),
      ]);
    }
    return m(".page-card.edit-form", [
      this.field("Keep processing after this", this.renderKeepProcessingCheckbox()),
      m("h2", "When"),
      m(MatcherNodeEditor, {
        node: this.matcherRoot,
        onChange: (updated) => (this.matcherRoot = updated),
        reputationLists: this.reputationLists,
      }),
      m("h2", "Then"),
      this.actionIsComposite ? this.renderCompositeActionNotice() : this.renderActionFields(),
      this.renderActions(),
    ]);
  }

  private renderKeepProcessingCheckbox(): m.Children {
    return m("label.checkbox-field", [
      m("input", {
        type: "checkbox", checked: this.keepProcessing,
        onchange: (e: Event) => (this.keepProcessing = (e.target as HTMLInputElement).checked),
      }),
      "Evaluate the rules after this one too",
    ]);
  }

  private renderCompositeActionNotice(): m.Children {
    return m(".composite-notice", [
      m("p", "This rule's action is a composite (AND/OR) — editing composite actions isn't supported here yet, so it's shown as-is and OK leaves it untouched."),
      m("ul.tree-root", renderActionNode(this.originalAction!)),
    ]);
  }

  private renderActionFields(): m.Children {
    const option = ACTION_TYPE_OPTIONS.find((o) => o.value === this.actionType)!;
    return [
      this.field("Action", m("select", {
        value: this.actionType,
        onchange: (e: Event) => (this.actionType = (e.target as HTMLSelectElement).value as ActionType),
      }, ACTION_TYPE_OPTIONS.map((o) => m("option", { value: o.value }, o.label)))),
      option.needsKey ? this.field(option.keyLabel || "Value", option.isFolderName ? this.renderFolderField() : m("input", {
        type: "text", value: this.actionKey, placeholder: option.keyPlaceholder,
        oninput: (e: Event) => (this.actionKey = (e.target as HTMLInputElement).value),
      })) : null,
    ];
  }

  /** A text input backed by a datalist (an "editable dropdown"): pick an existing folder from the account's real IMAP tree, or type one that doesn't exist yet — either way it's still free text underneath, so a typo just becomes a new folder name rather than a validation error. */
  private renderFolderField(): m.Children {
    return [
      m("input", {
        type: "text", value: this.actionKey, list: FOLDER_DATALIST_ID, placeholder: "Folder name",
        oninput: (e: Event) => (this.actionKey = (e.target as HTMLInputElement).value),
      }),
      m("datalist#" + FOLDER_DATALIST_ID, this.accountFolders.map((folder) => m("option", { key: folder, value: folder }))),
    ];
  }

  private renderActions(): m.Children {
    return m(".edit-actions", [
      m("button.ok-button", { onclick: () => this.apply() }, "OK"),
      m("button.cancel-button", { onclick: () => this.cancel() }, "Cancel"),
    ]);
  }

  private field(label: string, control: m.Children): m.Children {
    return m(".edit-field", [m("label", label), control]);
  }

  private cancel() {
    Routing.goToAccountSettings(this.accountName);
  }

  private load() {
    this.loading = true;
    this.error = undefined;
    Promise.all([
      accountEditSessions.load(this.accountName),
      ApiEndpoints.ReputationLists.call({}),
      // Listing folders needs a live IMAP connection (see AccountFoldersApi) — if that fails
      // (account temporarily unreachable, ...) the folder field should just fall back to plain
      // free text, not block editing an otherwise-unrelated rule.
      ApiEndpoints.AccountFolders.call({ accountName: this.accountName }).catch(() => ({ folders: [] as string[] })),
    ])
      .then(([session, reputationLists, accountFolders]) => {
        this.session = session;
        this.reputationLists = reputationLists.lists;
        this.accountFolders = accountFolders.folders;
        if (this.ruleIndex !== null) {
          const pendingRule = session.rules[this.ruleIndex];
          if (!pendingRule) {
            this.error = "No such rule.";
            this.loading = false;
            m.redraw();
            return;
          }
          this.applyRule(pendingRule.rule);
        }
        this.loading = false;
        m.redraw();
      })
      .catch((err: Error) => {
        this.loading = false;
        this.error = err.message;
        m.redraw();
      });
  }

  private applyRule(rule: MailFilterRuleConfiguration) {
    this.isLearnedRulesMarker = rule.type === RuleType.LEARNED_RULES;
    this.keepProcessing = rule.keepProcessing;
    if (this.isLearnedRulesMarker) return;

    this.matcherRoot = rule.matcher;

    const action = rule.action;
    this.originalAction = action;
    this.actionIsComposite = COMPOSITE_ACTION_TYPES.has(action.type);
    if (!this.actionIsComposite) {
      this.actionType = action.type;
      this.actionKey = action.key || "";
    }
  }

  private buildAction(): MailFilterRuleActionConfiguration {
    if (this.actionIsComposite && this.originalAction) return this.originalAction;
    const option = ACTION_TYPE_OPTIONS.find((o) => o.value === this.actionType)!;
    const action = { type: this.actionType } as MailFilterRuleActionConfiguration;
    if (option.needsKey) action.key = this.actionKey.trim();
    return action;
  }

  private validate(): string | undefined {
    if (this.isLearnedRulesMarker) return undefined;

    const matcherError = validateMatcherNode(this.matcherRoot);
    if (matcherError) return matcherError;

    if (!this.actionIsComposite) {
      const option = ACTION_TYPE_OPTIONS.find((o) => o.value === this.actionType)!;
      if (option.needsKey && this.actionKey.trim().length === 0) {
        return "The action needs a " + (option.keyLabel || "value").toLowerCase() + ".";
      }
    }
    return undefined;
  }

  private apply() {
    const validationError = this.validate();
    if (validationError) {
      this.error = validationError;
      return;
    }

    const rule: MailFilterRuleConfiguration = this.isLearnedRulesMarker
      ? ({ type: RuleType.LEARNED_RULES, keepProcessing: this.keepProcessing } as MailFilterRuleConfiguration)
      : { type: RuleType.MATCHER_ACTION_RULE, keepProcessing: this.keepProcessing, matcher: finalizeMatcherNode(this.matcherRoot), action: this.buildAction() };

    const session = this.session!;
    if (this.ruleIndex === null) {
      const newPendingRule: PendingRule = { rule, originalIndex: null, deleted: false, edited: false };
      session.rules = [...session.rules, newPendingRule];
    } else {
      const previous = session.rules[this.ruleIndex];
      const updated: PendingRule = { ...previous, rule, edited: true };
      session.rules = session.rules.map((pendingRule, index) => (index === this.ruleIndex ? updated : pendingRule));
    }

    Routing.goToAccountSettings(this.accountName);
  }
}
