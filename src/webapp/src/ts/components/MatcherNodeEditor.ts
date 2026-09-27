import m from "mithril";
import {
  DkimResult,
  DmarcPolicy,
  DmarcResult,
  FcrdnsResult,
  MailFilterRuleMatcherConfiguration,
  MatcherType,
  ReputationListDto,
  ReputationListType,
  SpfResult,
} from "../auto/pieroxy-mom";
import { TokenListEditor } from "./TokenListEditor";

type MatcherFieldKind = "keys" | "threshold" | "reputation";

interface LeafMatcherTypeOption {
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

const LEAF_MATCHER_TYPE_OPTIONS: LeafMatcherTypeOption[] = [
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

const THRESHOLD_PATTERN = /^[<>]=?\d+(\.\d+)?$/;

function isComposite(type: MatcherType): boolean {
  return type === MatcherType.AND || type === MatcherType.OR || type === MatcherType.NOT;
}

/** A fresh, empty leaf — the starting point for a brand-new rule, and for each new child a composite grows. */
export function defaultMatcherNode(): MailFilterRuleMatcherConfiguration {
  return { type: MatcherType.FROM_DOMAIN_EQUALS } as MailFilterRuleMatcherConfiguration;
}

/**
 * What should replace `previous` when the user picks `newType` in a node's own type dropdown —
 * preserves whatever can sensibly carry over instead of always starting blank:
 *  - leaf -> AND/OR: the leaf becomes condition #1, a fresh empty leaf becomes #2.
 *  - AND <-> OR: same children, just retagged.
 *  - composite -> NOT: keeps only the first child (NOT takes exactly one).
 *  - leaf -> NOT: wraps it.
 *  - leaf -> a different leaf of the *same* field kind (e.g. one "keys" matcher to another):
 *    keeps the typed value, exactly like changing the dropdown used to do when it was the only
 *    field on the page.
 *  - anything -> a leaf of a *different* kind, or composite -> leaf at all: starts fresh, since
 *    there's no field to carry a subtree's or a differently-shaped leaf's value into.
 */
export function convertMatcherNode(previous: MailFilterRuleMatcherConfiguration, newType: MatcherType): MailFilterRuleMatcherConfiguration {
  if (newType === previous.type) return previous;

  if (newType === MatcherType.AND || newType === MatcherType.OR) {
    if (isComposite(previous.type)) {
      const children = previous.type === MatcherType.NOT
        ? [...(previous.children || []), defaultMatcherNode()]
        : (previous.children || []);
      return { type: newType, children } as MailFilterRuleMatcherConfiguration;
    }
    return { type: newType, children: [previous, defaultMatcherNode()] } as MailFilterRuleMatcherConfiguration;
  }

  if (newType === MatcherType.NOT) {
    if (isComposite(previous.type)) {
      const firstChild = (previous.children && previous.children[0]) || defaultMatcherNode();
      return { type: newType, children: [firstChild] } as MailFilterRuleMatcherConfiguration;
    }
    return { type: newType, children: [previous] } as MailFilterRuleMatcherConfiguration;
  }

  // -> a leaf type.
  if (isComposite(previous.type)) {
    return { type: newType } as MailFilterRuleMatcherConfiguration;
  }
  const prevOption = LEAF_MATCHER_TYPE_OPTIONS.find((o) => o.value === previous.type);
  const nextOption = LEAF_MATCHER_TYPE_OPTIONS.find((o) => o.value === newType);
  if (prevOption && nextOption && prevOption.fields === nextOption.fields) {
    return { ...previous, type: newType } as MailFilterRuleMatcherConfiguration;
  }
  return { type: newType } as MailFilterRuleMatcherConfiguration;
}

/** Mirrors the checks the matcher implementations themselves make (see e.g. IpReputationMatcher), recursively — every leaf in the tree must be valid, and NOT/AND/OR must have the right number of children. */
export function validateMatcherNode(node: MailFilterRuleMatcherConfiguration): string | undefined {
  if (node.type === MatcherType.NOT) {
    if (!node.children || node.children.length !== 1) return "\"Not\" needs exactly one condition.";
    return validateMatcherNode(node.children[0]);
  }
  if (node.type === MatcherType.AND || node.type === MatcherType.OR) {
    if (!node.children || node.children.length === 0) {
      return "\"" + (node.type === MatcherType.AND ? "All of" : "Any of") + "\" needs at least one condition.";
    }
    for (const child of node.children) {
      const error = validateMatcherNode(child);
      if (error) return error;
    }
    return undefined;
  }

  const option = LEAF_MATCHER_TYPE_OPTIONS.find((o) => o.value === node.type)!;
  if (option.fields === "keys" && (!node.keys || node.keys.length === 0) && !node.key) {
    return "The matcher needs at least one key.";
  }
  if ((option.fields === "threshold" || option.fields === "reputation") && !THRESHOLD_PATTERN.test((node.key || "").trim())) {
    return "The threshold must look like \">0.9\" or \"<=0.2\".";
  }
  if (option.fields === "reputation" && (!node.listIds || node.listIds.length === 0)) {
    return "Pick at least one reputation list.";
  }
  return undefined;
}

/**
 * Recursively normalizes a matcher tree right before it's saved: collapses a single-element
 * "keys" array down to "key" (the shape a scalar match is stored as) and trims threshold text.
 * Editing itself always works with the array form / untrimmed text — only the final saved shape
 * cares about this distinction, so there's no need to juggle it on every keystroke.
 */
export function finalizeMatcherNode(node: MailFilterRuleMatcherConfiguration): MailFilterRuleMatcherConfiguration {
  if (isComposite(node.type)) {
    return { type: node.type, children: (node.children || []).map(finalizeMatcherNode) } as MailFilterRuleMatcherConfiguration;
  }
  const option = LEAF_MATCHER_TYPE_OPTIONS.find((o) => o.value === node.type)!;
  if (option.fields === "keys") {
    const keys = node.keys && node.keys.length > 0 ? node.keys : (node.key ? [node.key] : []);
    return keys.length === 1
      ? ({ type: node.type, key: keys[0] } as MailFilterRuleMatcherConfiguration)
      : ({ type: node.type, keys } as MailFilterRuleMatcherConfiguration);
  }
  if (option.fields === "reputation") {
    return { type: node.type, key: (node.key || "").trim(), listIds: node.listIds || [] } as MailFilterRuleMatcherConfiguration;
  }
  return { type: node.type, key: (node.key || "").trim() } as MailFilterRuleMatcherConfiguration;
}

interface MatcherNodeEditorAttrs {
  node: MailFilterRuleMatcherConfiguration;
  onChange: (updated: MailFilterRuleMatcherConfiguration) => void;
  /** Lets this node remove itself from its parent's children — absent at the tree's root, which can't be removed. */
  onRemove?: () => void;
  reputationLists: ReputationListDto[];
}

/**
 * Recursive, fully-controlled editor for one matcher node — a leaf (From equals, SPF result
 * equals, ...) or a composite (AND/OR/NOT) whose children are edited by nested instances of this
 * same component. Stateless by design: every edit computes a brand-new node and hands it to
 * onChange; the whole tree lives in the caller (RuleEditPage's matcherRoot), same top-down data
 * flow as TokenListEditor.
 */
export class MatcherNodeEditor implements m.ClassComponent<MatcherNodeEditorAttrs> {
  view({ attrs }: m.Vnode<MatcherNodeEditorAttrs>): m.Children {
    const { node, onChange, onRemove, reputationLists } = attrs;
    return m(".matcher-node", [
      m(".matcher-node-header", [
        m("select", {
          value: node.type,
          onchange: (e: Event) => onChange(convertMatcherNode(node, (e.target as HTMLSelectElement).value as MatcherType)),
        }, [
          m("optgroup", { label: "Match" }, LEAF_MATCHER_TYPE_OPTIONS.map((o) => m("option", { value: o.value }, o.label))),
          m("optgroup", { label: "Combine" }, [
            m("option", { value: MatcherType.AND }, "All of (AND)"),
            m("option", { value: MatcherType.OR }, "Any of (OR)"),
            m("option", { value: MatcherType.NOT }, "Not (NOT)"),
          ]),
        ]),
        onRemove ? m("span.matcher-node-remove", { title: "Remove this condition", onclick: onRemove }, "×") : null,
      ]),
      isComposite(node.type) ? this.renderChildren(node, onChange, reputationLists) : this.renderLeafFields(node, onChange, reputationLists),
    ]);
  }

  private renderChildren(node: MailFilterRuleMatcherConfiguration, onChange: (updated: MailFilterRuleMatcherConfiguration) => void,
                          reputationLists: ReputationListDto[]): m.Children {
    const children = node.children || [];
    const isNot = node.type === MatcherType.NOT;

    return m(".matcher-node-children", [
      children.map((child, index) => m(".matcher-node-child", { key: index }, m(MatcherNodeEditor, {
        node: child,
        onChange: (updated) => {
          const next = [...children];
          next[index] = updated;
          onChange({ ...node, children: next } as MailFilterRuleMatcherConfiguration);
        },
        onRemove: isNot ? undefined : () => onChange({ ...node, children: children.filter((_, i) => i !== index) } as MailFilterRuleMatcherConfiguration),
        reputationLists,
      }))),
      isNot ? null : m("button.matcher-add-condition", {
        onclick: () => onChange({ ...node, children: [...children, defaultMatcherNode()] } as MailFilterRuleMatcherConfiguration),
      }, "+ Add condition"),
    ]);
  }

  private renderLeafFields(node: MailFilterRuleMatcherConfiguration, onChange: (updated: MailFilterRuleMatcherConfiguration) => void,
                            reputationLists: ReputationListDto[]): m.Children {
    const option = LEAF_MATCHER_TYPE_OPTIONS.find((o) => o.value === node.type)!;
    const currentKeys = node.keys && node.keys.length > 0 ? node.keys : (node.key ? [node.key] : []);

    return m(".matcher-node-fields", [
      option.fields === "keys" ? field("Key(s)", m(TokenListEditor, {
        tokens: currentKeys,
        onChange: (keys) => onChange({ ...node, keys } as MailFilterRuleMatcherConfiguration),
        placeholder: "Value to match",
        options: option.options,
      })) : null,
      option.fields === "threshold" || option.fields === "reputation" ? field("Threshold", m("input", {
        type: "text", value: node.key || "", placeholder: ">0.9",
        oninput: (e: Event) => onChange({ ...node, key: (e.target as HTMLInputElement).value } as MailFilterRuleMatcherConfiguration),
      })) : null,
      option.fields === "reputation" ? field("Reputation list IDs", m(TokenListEditor, {
        tokens: node.listIds || [],
        onChange: (ids) => onChange({ ...node, listIds: ids } as MailFilterRuleMatcherConfiguration),
        options: reputationLists.filter((l) => l.type === option.reputationListType).map((l) => l.id),
      })) : null,
    ]);
  }
}

function field(label: string, control: m.Children): m.Children {
  return m(".edit-field", [m("label", label), control]);
}
