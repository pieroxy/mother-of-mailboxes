import m from "mithril";
import {
  ActionType,
  DkimResult,
  DmarcPolicy,
  DmarcResult,
  FcrdnsResult,
  MailFilterRuleActionConfiguration,
  MailFilterRuleConfiguration,
  MailFilterRuleMatcherConfiguration,
  MatcherType,
  ReputationListDto,
  ReputationListType,
  RuleType,
  SpfResult,
} from "../../auto/pieroxy-mom";
import { ApiEndpoints } from "../../auto/ApiEndpoints";
import { AbstractPage } from "./AbstractPage";
import { Routing } from "../../utils/navigation/Routing";
import { TokenListEditor } from "../TokenListEditor";
import { renderActionNode, renderMatcherNode } from "../RuleTree";
import { AccountEditSession, PendingRule, accountEditSessions } from "../../utils/AccountEditSession";

interface RuleEditPageAttrs {
  accountName: string;
  /** "new" to append a rule, or the rule's position in the session's rules list (as a string, straight from the route) to edit it. */
  ruleIndex: string;
}

type MatcherFieldKind = "keys" | "threshold" | "reputation";

interface MatcherTypeOption {
  value: MatcherType;
  label: string;
  fields: MatcherFieldKind;
  /** Restricts the "keys" field to this fixed set (an enum's values) — offered via a dropdown instead of free text. */
  options?: string[];
  /** For fields === "reputation": which kind of reputation list this matcher can reference — filters the list-id dropdown (see ReputationListsApi). */
  reputationListType?: ReputationListType;
}

// These five are @TypeScriptNonConstEnum on the Java side specifically so they have a real
// runtime object here (a plain TS enum, not the default const enum) — Object.values(...) lists
// every constant with no separate value list to keep in sync.
const SPF_RESULT_OPTIONS: string[] = Object.values(SpfResult);
const DKIM_RESULT_OPTIONS: string[] = Object.values(DkimResult);
const DMARC_RESULT_OPTIONS: string[] = Object.values(DmarcResult);
const DMARC_POLICY_OPTIONS: string[] = Object.values(DmarcPolicy);
const FCRDNS_RESULT_OPTIONS: string[] = Object.values(FcrdnsResult);

// Composite types (AND/OR/NOT) are deliberately excluded: editing a matcher/action tree isn't
// supported here yet — see COMPOSITE_MATCHER_TYPES/COMPOSITE_ACTION_TYPES below.
const MATCHER_TYPE_OPTIONS: MatcherTypeOption[] = [
  { value: MatcherType.FROM_EQUALS, label: "From (full header) equals", fields: "keys" },
  { value: MatcherType.FROM_ADDRESS_EQUALS, label: "From address equals", fields: "keys" },
  { value: MatcherType.FROM_DOMAIN_EQUALS, label: "From domain equals", fields: "keys" },
  { value: MatcherType.FROM_ADDRESS_REGEXP, label: "From address matches regexp", fields: "keys" },
  { value: MatcherType.SUBJECT_STARTS_WITH, label: "Subject starts with", fields: "keys" },
  { value: MatcherType.SPF_RESULT_EQUALS, label: "SPF result equals", fields: "keys", options: SPF_RESULT_OPTIONS },
  { value: MatcherType.DKIM_RESULT_EQUALS, label: "DKIM result equals", fields: "keys", options: DKIM_RESULT_OPTIONS },
  { value: MatcherType.DMARC_RESULT_EQUALS, label: "DMARC result equals", fields: "keys", options: DMARC_RESULT_OPTIONS },
  { value: MatcherType.DMARC_POLICY_EQUALS, label: "DMARC policy equals", fields: "keys", options: DMARC_POLICY_OPTIONS },
  { value: MatcherType.FCRDNS_RESULT_EQUALS, label: "FCrDNS result equals", fields: "keys", options: FCRDNS_RESULT_OPTIONS },
  { value: MatcherType.SUBJECT_CLASSIFIER_EQUALS, label: "Subject spam score", fields: "threshold" },
  { value: MatcherType.HEADER_CLASSIFIER_EQUALS, label: "Header spam score", fields: "threshold" },
  { value: MatcherType.BODY_CLASSIFIER_EQUALS, label: "Body spam score", fields: "threshold" },
  { value: MatcherType.IP_REPUTATION_EQUALS, label: "IP reputation score", fields: "reputation", reputationListType: ReputationListType.IP_CIDR },
  { value: MatcherType.FROM_DOMAIN_REPUTATION_EQUALS, label: "From domain reputation score", fields: "reputation", reputationListType: ReputationListType.DOMAIN },
];

interface ActionTypeOption {
  value: ActionType;
  label: string;
  needsKey: boolean;
  /** What the key field means for this action — shown as its label, since "key" means something different per type (a folder vs. a log message). */
  keyLabel?: string;
  keyPlaceholder?: string;
}

// AND/OR excluded, same reasoning as MATCHER_TYPE_OPTIONS.
const ACTION_TYPE_OPTIONS: ActionTypeOption[] = [
  { value: ActionType.MOVE_TO, label: "Move to folder", needsKey: true, keyLabel: "Destination folder" },
  { value: ActionType.MOVE_TO_AND_READ, label: "Move to folder and mark as read", needsKey: true, keyLabel: "Destination folder" },
  { value: ActionType.READ, label: "Mark as read", needsKey: false },
  {
    value: ActionType.NOOP, label: "Do nothing (log only)", needsKey: true,
    keyLabel: "Log message", keyPlaceholder: "What to log when this rule matches",
  },
];

const COMPOSITE_MATCHER_TYPES: Set<string> = new Set([MatcherType.AND, MatcherType.OR, MatcherType.NOT]);
const COMPOSITE_ACTION_TYPES: Set<string> = new Set([ActionType.AND, ActionType.OR]);

const THRESHOLD_PATTERN = /^[<>]=?\d+(\.\d+)?$/;

/**
 * Creates or edits one rule — reused for both, per its own route: "new" appends a rule to the
 * account's list, an index edits the rule already there. Only "leaf" matchers/actions are
 * editable; a rule whose matcher or action is a composite (AND/OR/NOT) shows read-only (see
 * RuleTree) and OK leaves that side untouched, only keepProcessing is still editable for it.
 * Nothing is sent to the backend here: "OK" just writes the edited rule into the account's
 * AccountEditSession and returns to the settings page, where it shows tagged "New"/"Edited" until
 * "Save Changes" persists the whole batch in one call (see AccountSettingsPage/UpdateAccountApi).
 */
export class RuleEditPage extends AbstractPage<RuleEditPageAttrs> {
  private accountName = "";
  private ruleIndex: number | null = null; // null = creating a new rule
  private session: AccountEditSession | undefined;
  private isLearnedRulesMarker = false;

  private keepProcessing = false;

  private matcherType: MatcherType = MatcherType.FROM_DOMAIN_EQUALS;
  private matcherKeys: string[] = [];
  private matcherThreshold = "";
  private matcherListIds: string[] = [];
  private matcherIsComposite = false;
  private originalMatcher: MailFilterRuleMatcherConfiguration | undefined;
  private reputationLists: ReputationListDto[] = [];

  private actionType: ActionType = ActionType.MOVE_TO;
  private actionKey = "";
  private actionIsComposite = false;
  private originalAction: MailFilterRuleActionConfiguration | undefined;

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
      this.matcherIsComposite ? this.renderCompositeNotice("matcher", renderMatcherNode(this.originalMatcher!)) : this.renderMatcherFields(),
      m("h2", "Then"),
      this.actionIsComposite ? this.renderCompositeNotice("action", renderActionNode(this.originalAction!)) : this.renderActionFields(),
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

  private renderCompositeNotice(kind: string, tree: m.Children): m.Children {
    return m(".composite-notice", [
      m("p", "This rule's " + kind + " is a composite (AND/OR/NOT) — editing composites isn't supported here yet, so it's shown as-is and OK leaves it untouched."),
      m("ul.tree-root", tree),
    ]);
  }

  private renderMatcherFields(): m.Children {
    const option = MATCHER_TYPE_OPTIONS.find((o) => o.value === this.matcherType)!;
    return [
      this.field("Matcher", m("select", {
        value: this.matcherType,
        onchange: (e: Event) => (this.matcherType = (e.target as HTMLSelectElement).value as MatcherType),
      }, MATCHER_TYPE_OPTIONS.map((o) => m("option", { value: o.value }, o.label)))),
      option.fields === "keys" ? this.field("Key(s)", m(TokenListEditor, {
        tokens: this.matcherKeys,
        onChange: (keys) => (this.matcherKeys = keys),
        placeholder: "Value to match",
        options: option.options,
      })) : null,
      option.fields === "threshold" || option.fields === "reputation" ? this.field("Threshold", m("input", {
        type: "text", value: this.matcherThreshold, placeholder: ">0.9",
        oninput: (e: Event) => (this.matcherThreshold = (e.target as HTMLInputElement).value),
      })) : null,
      option.fields === "reputation" ? this.field("Reputation list IDs", m(TokenListEditor, {
        tokens: this.matcherListIds,
        onChange: (ids) => (this.matcherListIds = ids),
        options: this.reputationLists.filter((l) => l.type === option.reputationListType).map((l) => l.id),
      })) : null,
    ];
  }

  private renderActionFields(): m.Children {
    const option = ACTION_TYPE_OPTIONS.find((o) => o.value === this.actionType)!;
    return [
      this.field("Action", m("select", {
        value: this.actionType,
        onchange: (e: Event) => (this.actionType = (e.target as HTMLSelectElement).value as ActionType),
      }, ACTION_TYPE_OPTIONS.map((o) => m("option", { value: o.value }, o.label)))),
      option.needsKey ? this.field(option.keyLabel || "Value", m("input", {
        type: "text", value: this.actionKey, placeholder: option.keyPlaceholder,
        oninput: (e: Event) => (this.actionKey = (e.target as HTMLInputElement).value),
      })) : null,
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
    Promise.all([accountEditSessions.load(this.accountName), ApiEndpoints.ReputationLists.call({})])
      .then(([session, reputationLists]) => {
        this.session = session;
        this.reputationLists = reputationLists.lists;
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

    const matcher = rule.matcher;
    this.originalMatcher = matcher;
    this.matcherIsComposite = COMPOSITE_MATCHER_TYPES.has(matcher.type);
    if (!this.matcherIsComposite) {
      this.matcherType = matcher.type;
      this.matcherKeys = matcher.keys && matcher.keys.length > 0 ? matcher.keys : (matcher.key ? [matcher.key] : []);
      this.matcherThreshold = matcher.key || "";
      this.matcherListIds = matcher.listIds || [];
    }

    const action = rule.action;
    this.originalAction = action;
    this.actionIsComposite = COMPOSITE_ACTION_TYPES.has(action.type);
    if (!this.actionIsComposite) {
      this.actionType = action.type;
      this.actionKey = action.key || "";
    }
  }

  private buildMatcher(): MailFilterRuleMatcherConfiguration {
    if (this.matcherIsComposite && this.originalMatcher) return this.originalMatcher;
    const option = MATCHER_TYPE_OPTIONS.find((o) => o.value === this.matcherType)!;
    const matcher = { type: this.matcherType } as MailFilterRuleMatcherConfiguration;
    if (option.fields === "keys") {
      if (this.matcherKeys.length === 1) matcher.key = this.matcherKeys[0];
      else matcher.keys = this.matcherKeys;
    } else {
      matcher.key = this.matcherThreshold.trim();
      if (option.fields === "reputation") matcher.listIds = this.matcherListIds;
    }
    return matcher;
  }

  private buildAction(): MailFilterRuleActionConfiguration {
    if (this.actionIsComposite && this.originalAction) return this.originalAction;
    const option = ACTION_TYPE_OPTIONS.find((o) => o.value === this.actionType)!;
    const action = { type: this.actionType } as MailFilterRuleActionConfiguration;
    if (option.needsKey) action.key = this.actionKey.trim();
    return action;
  }

  /** Mirrors the checks the matcher/action implementations themselves make (see e.g. IpReputationMatcher) — catches the common mistakes before they're staged into the session. */
  private validate(): string | undefined {
    if (this.isLearnedRulesMarker) return undefined;
    if (!this.matcherIsComposite) {
      const option = MATCHER_TYPE_OPTIONS.find((o) => o.value === this.matcherType)!;
      if (option.fields === "keys" && this.matcherKeys.length === 0) {
        return "The matcher needs at least one key.";
      }
      if ((option.fields === "threshold" || option.fields === "reputation") && !THRESHOLD_PATTERN.test(this.matcherThreshold.trim())) {
        return "The threshold must look like \">0.9\" or \"<=0.2\".";
      }
      if (option.fields === "reputation" && this.matcherListIds.length === 0) {
        return "Pick at least one reputation list.";
      }
    }
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
      : { type: RuleType.MATCHER_ACTION_RULE, keepProcessing: this.keepProcessing, matcher: this.buildMatcher(), action: this.buildAction() };

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
