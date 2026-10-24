import m from "mithril";
import {
  MailFilterRuleActionConfiguration,
  MailFilterRuleConfiguration,
  MailFilterRuleMatcherConfiguration,
  ReputationListDto,
  RuleType,
} from "../../auto/pieroxy-mom";
import { ApiEndpoints } from "../../auto/ApiEndpoints";
import { AbstractPage } from "./AbstractPage";
import { Routing } from "../../utils/navigation/Routing";
import { defaultActionName } from "../../utils/actionNames";
import { AccountEditSession, PendingRule, accountEditSessions } from "../../utils/AccountEditSession";
import { MatcherNodeEditor, defaultMatcherNode, finalizeMatcherNode, validateMatcherNode } from "../MatcherNodeEditor";
import {
  ActionNodeEditor,
  FOLDER_DATALIST_ID,
  defaultActionNode,
  finalizeActionNode,
  validateActionNode,
} from "../ActionNodeEditor";

interface RuleEditPageAttrs {
  accountName: string;
  /** "new" to append a rule, or the rule's position in the session's rules list (as a string, straight from the route) to edit it. */
  ruleIndex: string;
}

/**
 * Creates or edits one rule — reused for both, per its own route: "new" appends a rule to the
 * account's list, an index edits the rule already there. The matcher side ("When") is a fully
 * editable tree, including AND/OR/NOT composites — see MatcherNodeEditor — and so is the action
 * side ("Then"), including AND/OR composites — see ActionNodeEditor. Nothing is sent to the backend here: "OK" just
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
  private ruleName = "";

  private matcherRoot: MailFilterRuleMatcherConfiguration = defaultMatcherNode();
  private reputationLists: ReputationListDto[] = [];

  /** The action tree, without the rule-level name and spam flag (actionName/actionSpam), which only live on its root. */
  private actionRoot: MailFilterRuleActionConfiguration = defaultActionNode();
  private actionName = "";
  private actionSpam = false;
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
        this.renderRuleKindField(),
        m(".rule-learned-marker", "This is the account's \"learned rules\" marker — it has no matcher or action of its own."),
        this.field("Keep processing after this", this.renderKeepProcessingCheckbox()),
        this.renderActions(),
      ]);
    }
    return m(".page-card.edit-form", [
      this.renderRuleKindField(),
      this.field("Rule name", m("input", {
        type: "text", value: this.ruleName, placeholder: "Optional, e.g. Backups",
        title: "Shown in the rule list and the decision log",
        oninput: (e: Event) => (this.ruleName = (e.target as HTMLInputElement).value),
      })),
      this.field("Keep processing after this", this.renderKeepProcessingCheckbox()),
      m("h2", "When"),
      m(MatcherNodeEditor, {
        node: this.matcherRoot,
        onChange: (updated) => (this.matcherRoot = updated),
        reputationLists: this.reputationLists,
      }),
      m("h2", "Then"),
      m(ActionNodeEditor, {
        node: this.actionRoot,
        onChange: (updated) => (this.actionRoot = updated),
        hasSmtp: !!this.session?.workingSmtp,
      }),
      // One for every folder field of the tree: an "editable dropdown" of the account's real
      // folders (see AccountFoldersApi) — still free text, so a new name just creates a folder.
      m("datalist#" + FOLDER_DATALIST_ID, this.accountFolders.map((folder) => m("option", { key: folder, value: folder }))),
      this.renderActionLabelFields(),
      this.renderActions(),
    ]);
  }

  /**
   * Lets a rule be created (or converted) as the account's "learned rules" marker instead of a
   * plain matcher/action rule — see RuleType.LEARNED_RULES. Only one may exist per account (see
   * RuleCatalog.validateRules), enforced in validate() below, not here: the dropdown itself stays
   * simple and always offers both options.
   */
  private renderRuleKindField(): m.Children {
    return this.field("Rule type", m("select", {
      value: this.isLearnedRulesMarker ? "LEARNED_RULES" : "MATCHER_ACTION_RULE",
      onchange: (e: Event) => (this.isLearnedRulesMarker = (e.target as HTMLSelectElement).value === "LEARNED_RULES"),
    }, [
      m("option", { value: "MATCHER_ACTION_RULE" }, "Matcher / Action"),
      m("option", { value: "LEARNED_RULES" }, "Run learned rules here"),
    ]));
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

  /** How the decision log shows this action (see MailFilterRuleActionConfiguration#name/spam). */
  private renderActionLabelFields(): m.Children {
    return [
      this.field("Action name", m("input", {
        type: "text", value: this.actionName, placeholder: defaultActionName(this.actionRoot),
        title: "Shown in the decision log; leave empty for the generated name",
        oninput: (e: Event) => (this.actionName = (e.target as HTMLInputElement).value),
      })),
      this.field("Spam", m("label.checkbox-field", [
        m("input", {
          type: "checkbox", checked: this.actionSpam,
          onchange: (e: Event) => (this.actionSpam = (e.target as HTMLInputElement).checked),
        }),
        "This action filters spam (display only: highlights it in the decision log)",
      ])),
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
    this.ruleName = rule.name || "";
    if (this.isLearnedRulesMarker) return;

    this.matcherRoot = rule.matcher;

    const action = { ...rule.action } as Partial<MailFilterRuleActionConfiguration>;
    this.actionName = action.name || "";
    this.actionSpam = action.spam === true;
    delete action.name;
    delete action.spam;
    this.actionRoot = action as MailFilterRuleActionConfiguration;
  }

  private buildAction(): MailFilterRuleActionConfiguration {
    const action = finalizeActionNode(this.actionRoot);
    // Unset rather than empty/false, so config.json stays free of default values.
    if (this.actionName.trim()) action.name = this.actionName.trim();
    if (this.actionSpam) action.spam = true;
    return action;
  }

  /** Mirrors RuleCatalog.validateRules — catches the common mistakes before they're staged into the session instead of only surfacing them at "Save Changes" time. */
  private validate(): string | undefined {
    if (this.isLearnedRulesMarker) {
      const session = this.session!;
      const collides = session.rules.some((pending, index) =>
        index !== this.ruleIndex && !pending.deleted && pending.rule.type === RuleType.LEARNED_RULES);
      if (collides) return "Only one \"Run learned rules here\" entry is allowed per account.";
      return undefined;
    }

    const matcherError = validateMatcherNode(this.matcherRoot);
    if (matcherError) return matcherError;

    return validateActionNode(this.actionRoot, !!this.session!.workingSmtp);
  }

  private apply() {
    const validationError = this.validate();
    if (validationError) {
      this.error = validationError;
      return;
    }

    const rule: MailFilterRuleConfiguration = this.isLearnedRulesMarker
      ? ({ type: RuleType.LEARNED_RULES, keepProcessing: this.keepProcessing } as MailFilterRuleConfiguration)
      : { type: RuleType.MATCHER_ACTION_RULE, keepProcessing: this.keepProcessing, matcher: finalizeMatcherNode(this.matcherRoot), action: this.buildAction() } as MailFilterRuleConfiguration;
    if (!this.isLearnedRulesMarker && this.ruleName.trim()) rule.name = this.ruleName.trim();

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
