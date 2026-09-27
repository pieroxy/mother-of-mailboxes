import m from "mithril";
import { ApiEndpoints } from "../../auto/ApiEndpoints";
import {
  ActionType,
  MailFilterRuleActionConfiguration,
  MailFilterRuleConfiguration,
  MailFilterRuleMatcherConfiguration,
  MatcherType,
  RuleType,
} from "../../auto/pieroxy-mom";
import { AbstractPage } from "./AbstractPage";
import { Routing } from "../../utils/navigation/Routing";
import { TokenListEditor } from "../TokenListEditor";
import { renderActionNode, renderMatcherNode } from "../RuleTree";

interface RuleEditPageAttrs {
  accountName: string;
  /** "new" to append a rule, or the rule's index (as a string, straight from the route) to edit it. */
  ruleIndex: string;
}

type MatcherFieldKind = "keys" | "threshold" | "reputation";

interface MatcherTypeOption {
  value: MatcherType;
  label: string;
  fields: MatcherFieldKind;
}

// Composite types (AND/OR/NOT) are deliberately excluded: editing a matcher/action tree isn't
// supported here yet — see COMPOSITE_MATCHER_TYPES/COMPOSITE_ACTION_TYPES below.
const MATCHER_TYPE_OPTIONS: MatcherTypeOption[] = [
  { value: MatcherType.FROM_EQUALS, label: "From (full header) equals", fields: "keys" },
  { value: MatcherType.FROM_ADDRESS_EQUALS, label: "From address equals", fields: "keys" },
  { value: MatcherType.FROM_DOMAIN_EQUALS, label: "From domain equals", fields: "keys" },
  { value: MatcherType.FROM_ADDRESS_REGEXP, label: "From address matches regexp", fields: "keys" },
  { value: MatcherType.SUBJECT_STARTS_WITH, label: "Subject starts with", fields: "keys" },
  { value: MatcherType.SPF_RESULT_EQUALS, label: "SPF result equals", fields: "keys" },
  { value: MatcherType.DKIM_RESULT_EQUALS, label: "DKIM result equals", fields: "keys" },
  { value: MatcherType.DMARC_RESULT_EQUALS, label: "DMARC result equals", fields: "keys" },
  { value: MatcherType.DMARC_POLICY_EQUALS, label: "DMARC policy equals", fields: "keys" },
  { value: MatcherType.FCRDNS_RESULT_EQUALS, label: "FCrDNS result equals", fields: "keys" },
  { value: MatcherType.SUBJECT_CLASSIFIER_EQUALS, label: "Subject spam score", fields: "threshold" },
  { value: MatcherType.HEADER_CLASSIFIER_EQUALS, label: "Header spam score", fields: "threshold" },
  { value: MatcherType.BODY_CLASSIFIER_EQUALS, label: "Body spam score", fields: "threshold" },
  { value: MatcherType.IP_REPUTATION_EQUALS, label: "IP reputation score", fields: "reputation" },
  { value: MatcherType.FROM_DOMAIN_REPUTATION_EQUALS, label: "From domain reputation score", fields: "reputation" },
];

interface ActionTypeOption {
  value: ActionType;
  label: string;
  needsKey: boolean;
}

// AND/OR excluded, same reasoning as MATCHER_TYPE_OPTIONS.
const ACTION_TYPE_OPTIONS: ActionTypeOption[] = [
  { value: ActionType.MOVE_TO, label: "Move to folder", needsKey: true },
  { value: ActionType.MOVE_TO_AND_READ, label: "Move to folder and mark as read", needsKey: true },
  { value: ActionType.READ, label: "Mark as read", needsKey: false },
  { value: ActionType.NOOP, label: "Do nothing (log only)", needsKey: false },
];

const COMPOSITE_MATCHER_TYPES: Set<string> = new Set([MatcherType.AND, MatcherType.OR, MatcherType.NOT]);
const COMPOSITE_ACTION_TYPES: Set<string> = new Set([ActionType.AND, ActionType.OR]);

const THRESHOLD_PATTERN = /^[<>]=?\d+(\.\d+)?$/;

/**
 * Creates or edits one rule — reused for both, per its own route: "new" appends a rule to the
 * account's list, an index edits the rule already there. Only "leaf" matchers/actions are
 * editable; a rule whose matcher or action is a composite (AND/OR/NOT) shows read-only (see
 * RuleTree) and Save leaves that side untouched, only keepProcessing is still editable for it.
 * Saving posts the account's *whole* rules list (see UpdateAccountRulesApi — the same endpoint
 * the settings page's reorder/delete already uses) and restarts the account.
 */
export class RuleEditPage extends AbstractPage<RuleEditPageAttrs> {
  private accountName = "";
  private ruleIndex: number | null = null; // null = creating a new rule
  private allRules: MailFilterRuleConfiguration[] = [];
  private isLearnedRulesMarker = false;

  private keepProcessing = false;

  private matcherType: MatcherType = MatcherType.FROM_DOMAIN_EQUALS;
  private matcherKeys: string[] = [];
  private matcherThreshold = "";
  private matcherListIds: string[] = [];
  private matcherIsComposite = false;
  private originalMatcher: MailFilterRuleMatcherConfiguration | undefined;

  private actionType: ActionType = ActionType.MOVE_TO;
  private actionKey = "";
  private actionIsComposite = false;
  private originalAction: MailFilterRuleActionConfiguration | undefined;

  private loading = true;
  private saving = false;
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
        type: "checkbox", checked: this.keepProcessing, disabled: this.saving,
        onchange: (e: Event) => (this.keepProcessing = (e.target as HTMLInputElement).checked),
      }),
      "Evaluate the rules after this one too",
    ]);
  }

  private renderCompositeNotice(kind: string, tree: m.Children): m.Children {
    return m(".composite-notice", [
      m("p", "This rule's " + kind + " is a composite (AND/OR/NOT) — editing composites isn't supported here yet, so it's shown as-is and Save leaves it untouched."),
      m("ul.tree-root", tree),
    ]);
  }

  private renderMatcherFields(): m.Children {
    const option = MATCHER_TYPE_OPTIONS.find((o) => o.value === this.matcherType)!;
    return [
      this.field("Matcher", m("select", {
        value: this.matcherType, disabled: this.saving,
        onchange: (e: Event) => (this.matcherType = (e.target as HTMLSelectElement).value as MatcherType),
      }, MATCHER_TYPE_OPTIONS.map((o) => m("option", { value: o.value }, o.label)))),
      option.fields === "keys" ? this.field("Key(s)", m(TokenListEditor, {
        tokens: this.matcherKeys,
        onChange: (keys) => (this.matcherKeys = keys),
        placeholder: "Value to match",
        disabled: this.saving,
      })) : null,
      option.fields === "threshold" || option.fields === "reputation" ? this.field("Threshold", m("input", {
        type: "text", value: this.matcherThreshold, placeholder: ">0.9", disabled: this.saving,
        oninput: (e: Event) => (this.matcherThreshold = (e.target as HTMLInputElement).value),
      })) : null,
      option.fields === "reputation" ? this.field("Reputation list IDs", m(TokenListEditor, {
        tokens: this.matcherListIds,
        onChange: (ids) => (this.matcherListIds = ids),
        placeholder: "List ID",
        disabled: this.saving,
      })) : null,
    ];
  }

  private renderActionFields(): m.Children {
    const option = ACTION_TYPE_OPTIONS.find((o) => o.value === this.actionType)!;
    return [
      this.field("Action", m("select", {
        value: this.actionType, disabled: this.saving,
        onchange: (e: Event) => (this.actionType = (e.target as HTMLSelectElement).value as ActionType),
      }, ACTION_TYPE_OPTIONS.map((o) => m("option", { value: o.value }, o.label)))),
      option.needsKey ? this.field("Destination folder", m("input", {
        type: "text", value: this.actionKey, disabled: this.saving,
        oninput: (e: Event) => (this.actionKey = (e.target as HTMLInputElement).value),
      })) : null,
    ];
  }

  private renderActions(): m.Children {
    return m(".edit-actions", [
      m("button.save-button", { onclick: () => this.save(), disabled: this.saving }, this.saving ? "Saving…" : "Save"),
      m("button.cancel-button", { onclick: () => this.cancel(), disabled: this.saving }, "Cancel"),
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
    ApiEndpoints.AccountConfig.call({ accountName: this.accountName })
      .then((output) => {
        this.allRules = output.rules;
        if (this.ruleIndex !== null) {
          const rule = this.allRules[this.ruleIndex];
          if (!rule) {
            this.error = "No such rule.";
            this.loading = false;
            m.redraw();
            return;
          }
          this.applyRule(rule);
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

  /** Mirrors the checks the matcher/action implementations themselves make (see e.g. IpReputationMatcher) — catches the common mistakes before they ever reach the account restart. */
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
        return "The action needs a destination folder.";
      }
    }
    return undefined;
  }

  private save() {
    const validationError = this.validate();
    if (validationError) {
      this.error = validationError;
      return;
    }

    const rule: MailFilterRuleConfiguration = this.isLearnedRulesMarker
      ? ({ type: RuleType.LEARNED_RULES, keepProcessing: this.keepProcessing } as MailFilterRuleConfiguration)
      : { type: RuleType.MATCHER_ACTION_RULE, keepProcessing: this.keepProcessing, matcher: this.buildMatcher(), action: this.buildAction() };

    const rules = [...this.allRules];
    if (this.ruleIndex === null) {
      rules.push(rule);
    } else {
      rules[this.ruleIndex] = rule;
    }

    this.saving = true;
    this.error = undefined;
    ApiEndpoints.UpdateAccountRules.call({ accountName: this.accountName, rules })
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
