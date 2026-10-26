import m from "mithril";
import { ActionType, MailFilterRuleActionConfiguration } from "../auto/pieroxy-mom";
import { AddCard } from "./AddCard";
import { LogLevelSelect } from "./LogLevelSelect";

type ActionKeyKind = "folder" | "recipient" | "text" | "subject";

interface LeafActionTypeOption {
  value: ActionType;
  label: string;
  /** Absent: the action takes no key. */
  keyKind?: ActionKeyKind;
  /** What the key field means for this action — shown as its label, since "key" means something different per type. */
  keyLabel?: string;
  keyPlaceholder?: string;
  /** The key may be left blank. */
  keyOptional?: boolean;
  /** Takes a multi-line body as well (see MailFilterRuleActionConfiguration#body). */
  hasBody?: boolean;
  /** Sends mail, so needs the account's SMTP settings. */
  sends?: boolean;
}

const LEAF_ACTION_TYPE_OPTIONS: LeafActionTypeOption[] = [
  { value: ActionType.MOVE_TO, label: "Move to folder", keyKind: "folder", keyLabel: "Destination folder", keyPlaceholder: "Folder name" },
  { value: ActionType.MOVE_TO_AND_READ, label: "Move to folder and mark as read", keyKind: "folder", keyLabel: "Destination folder", keyPlaceholder: "Folder name" },
  { value: ActionType.READ, label: "Mark as read" },
  { value: ActionType.NOOP, label: "Do nothing (log only)", keyKind: "text", keyLabel: "Log message", keyPlaceholder: "What to log when this rule matches" },
  { value: ActionType.FORWARD, label: "Forward (as attachment)", keyKind: "recipient", keyLabel: "Recipient address", keyPlaceholder: "someone@example.com", sends: true },
  { value: ActionType.REDIRECT, label: "Redirect (resend unchanged)", keyKind: "recipient", keyLabel: "Recipient address", keyPlaceholder: "someone@example.com", sends: true },
  { value: ActionType.REPLY, label: "Reply to the sender", keyKind: "subject", keyLabel: "Subject", keyPlaceholder: "Blank: \"Re: \" + the original subject",
    keyOptional: true, hasBody: true, sends: true },
];

/** The id of the folder datalist the page renders once, which every folder field points to. */
export const FOLDER_DATALIST_ID = "destination-folder-options";

function isComposite(type: ActionType): boolean {
  return type === ActionType.AND || type === ActionType.OR;
}

function leafOption(type: ActionType): LeafActionTypeOption | undefined {
  return LEAF_ACTION_TYPE_OPTIONS.find((o) => o.value === type);
}

/** A fresh, empty leaf — the starting point for a brand-new rule, and for each new child a composite grows. */
export function defaultActionNode(): MailFilterRuleActionConfiguration {
  return { type: ActionType.MOVE_TO } as MailFilterRuleActionConfiguration;
}

/**
 * What should replace `previous` when the user picks `newType` in a node's own type dropdown —
 * same idea as convertMatcherNode:
 *  - leaf -> AND/OR: the leaf becomes action #1, a fresh empty leaf becomes #2.
 *  - AND <-> OR: same children, just retagged.
 *  - leaf -> a leaf with the same kind of key (folder, recipient…): keeps the typed value, and the body.
 *  - anything else: starts fresh.
 */
export function convertActionNode(previous: MailFilterRuleActionConfiguration, newType: ActionType): MailFilterRuleActionConfiguration {
  if (newType === previous.type) return previous;
  let converted: MailFilterRuleActionConfiguration;
  if (isComposite(newType)) {
    converted = isComposite(previous.type)
      ? { type: newType, children: previous.children || [] } as MailFilterRuleActionConfiguration
      : { type: newType, children: [previous, defaultActionNode()] } as MailFilterRuleActionConfiguration;
  } else {
    const prev = leafOption(previous.type);
    const next = leafOption(newType);
    converted = prev?.keyKind && prev.keyKind === next?.keyKind
      ? { type: newType, key: previous.key } as MailFilterRuleActionConfiguration
      : { type: newType } as MailFilterRuleActionConfiguration;
    if (prev?.hasBody && next?.hasBody && previous.body !== undefined) converted.body = previous.body;
  }
  // The log level belongs to the node, whatever its type — unless the node was wrapped as a child, which keeps its own.
  const wrapped = (converted.children || []).includes(previous);
  return previous.logLevel && !wrapped ? withLogLevel(converted, previous.logLevel) : converted;
}

/** Mirrors the checks the actions themselves make, recursively. {@code hasSmtp}: whether the account can send mail. */
export function validateActionNode(node: MailFilterRuleActionConfiguration, hasSmtp: boolean): string | undefined {
  if (isComposite(node.type)) {
    if (!node.children || node.children.length === 0) {
      return "\"" + compositeLabel(node.type) + "\" needs at least one action.";
    }
    for (const child of node.children) {
      const error = validateActionNode(child, hasSmtp);
      if (error) return error;
    }
    return undefined;
  }
  const option = leafOption(node.type);
  if (!option) return "Unknown action type " + node.type + ".";
  const key = (node.key || "").trim();
  if (option.keyKind && !option.keyOptional && key.length === 0) return "The action needs a " + (option.keyLabel || "value").toLowerCase() + ".";
  if (option.keyKind === "recipient" && !/^[^@\s,]+@[^@\s,]+$/.test(key)) return "The recipient must be a single valid email address.";
  if (option.hasBody && !(node.body || "").trim()) return "The reply needs a text.";
  if (option.sends && !hasSmtp) return "Set up this account's SMTP settings first: this action sends mail through them.";
  return undefined;
}

/**
 * The tree as it's saved: trimmed keys (blank optional ones dropped), no field a node's type doesn't use (except logLevel).
 * The rule-level name and spam flag aren't part of the tree: the page sets them on the root.
 */
export function finalizeActionNode(node: MailFilterRuleActionConfiguration): MailFilterRuleActionConfiguration {
  if (isComposite(node.type)) {
    const composite = { type: node.type, children: (node.children || []).map(finalizeActionNode) } as MailFilterRuleActionConfiguration;
    if (node.logLevel) composite.logLevel = node.logLevel;
    return composite;
  }
  const option = leafOption(node.type);
  const finalized = { type: node.type } as MailFilterRuleActionConfiguration;
  const key = (node.key || "").trim();
  if (option?.keyKind && (key || !option.keyOptional)) finalized.key = key;
  if (option?.hasBody) finalized.body = node.body || "";
  if (node.logLevel) finalized.logLevel = node.logLevel;
  return finalized;
}

function compositeLabel(type: ActionType): string {
  return type === ActionType.AND ? "All of, in order" : "First that succeeds";
}

interface ActionNodeEditorAttrs {
  node: MailFilterRuleActionConfiguration;
  onChange: (updated: MailFilterRuleActionConfiguration) => void;
  /** Lets this node remove itself from its parent's children — absent at the tree's root, which can't be removed. */
  onRemove?: () => void;
  /** Whether the account has SMTP settings, which forwarding actions need. */
  hasSmtp: boolean;
}

/**
 * Recursive, fully-controlled editor for one action node — a leaf (Move to, Forward…) or a
 * composite (AND: all of them in order, stopping at the first failure; OR: the first that
 * succeeds) whose children are edited by nested instances of this same component. Stateless, like
 * MatcherNodeEditor: every edit builds a new node handed to onChange; the tree lives in the page.
 */
export class ActionNodeEditor implements m.ClassComponent<ActionNodeEditorAttrs> {
  view({ attrs }: m.Vnode<ActionNodeEditorAttrs>): m.Children {
    const { node, onChange, onRemove, hasSmtp } = attrs;
    return m(".matcher-node.action-node", [
      m(".matcher-node-header", [
        m("select", {
          value: node.type,
          onchange: (e: Event) => onChange(convertActionNode(node, (e.target as HTMLSelectElement).value as ActionType)),
        }, [
          m("optgroup", { label: "Do" }, LEAF_ACTION_TYPE_OPTIONS.map((o) => m("option", { value: o.value }, o.label))),
          m("optgroup", { label: "Combine" }, [
            m("option", { value: ActionType.AND }, "All of, in order (AND)"),
            m("option", { value: ActionType.OR }, "First that succeeds (OR)"),
          ]),
        ]),
        m(LogLevelSelect, {
          value: node.logLevel,
          onChange: (logLevel) => onChange(withLogLevel(node, logLevel)),
        }),
        onRemove ? m("span.matcher-node-remove", { title: "Remove this action", onclick: onRemove }, "×") : null,
      ]),
      isComposite(node.type) ? this.renderChildren(node, onChange, hasSmtp) : this.renderLeafFields(node, onChange, hasSmtp),
    ]);
  }

  private renderChildren(node: MailFilterRuleActionConfiguration, onChange: (updated: MailFilterRuleActionConfiguration) => void,
                         hasSmtp: boolean): m.Children {
    const children = node.children || [];
    return m(".matcher-node-children", [
      m("p.field-hint", node.type === ActionType.AND
        ? "Runs every action below in order, stopping at the first one that fails."
        : "Tries the actions below in order, stopping at the first one that succeeds."),
      children.map((child, index) => m(".matcher-node-child", { key: index }, m(ActionNodeEditor, {
        node: child,
        onChange: (updated) => {
          const next = [...children];
          next[index] = updated;
          onChange({ ...node, children: next } as MailFilterRuleActionConfiguration);
        },
        onRemove: () => onChange({ ...node, children: children.filter((_, i) => i !== index) } as MailFilterRuleActionConfiguration),
        hasSmtp,
      }))),
      m(AddCard, {
        label: "Add action", title: "Add an action to this group",
        onclick: () => onChange({ ...node, children: [...children, defaultActionNode()] } as MailFilterRuleActionConfiguration),
      }),
    ]);
  }

  private renderLeafFields(node: MailFilterRuleActionConfiguration, onChange: (updated: MailFilterRuleActionConfiguration) => void,
                           hasSmtp: boolean): m.Children {
    const option = leafOption(node.type);
    if (!option?.keyKind) return null;
    return m(".matcher-node-fields", [
      m(".edit-field", [
        m("label", option.keyLabel || "Value"),
        m("input", {
          type: option.keyKind === "recipient" ? "email" : "text",
          value: node.key || "",
          placeholder: option.keyPlaceholder,
          list: option.keyKind === "folder" ? FOLDER_DATALIST_ID : undefined,
          oninput: (e: Event) => onChange({ ...node, key: (e.target as HTMLInputElement).value } as MailFilterRuleActionConfiguration),
        }),
      ]),
      option.hasBody ? m(".edit-field", [
        m("label", "Text"),
        m("textarea", {
          rows: 6,
          value: node.body || "",
          placeholder: "Plain text",
          oninput: (e: Event) => onChange({ ...node, body: (e.target as HTMLTextAreaElement).value } as MailFilterRuleActionConfiguration),
        }),
        m("p.field-hint", "Never sent to automated messages, mailing lists, bounces, no-reply addresses or this account's own messages."),
      ]) : null,
      option.sends && !hasSmtp
        ? m("p.field-hint", "This account has no SMTP settings yet: add them on the settings page before saving this rule.")
        : null,
    ]);
  }
}

function withLogLevel(node: MailFilterRuleActionConfiguration, logLevel: string | undefined): MailFilterRuleActionConfiguration {
  const updated = { ...node } as Partial<MailFilterRuleActionConfiguration>;
  delete updated.logLevel;
  if (logLevel) updated.logLevel = logLevel;
  return updated as MailFilterRuleActionConfiguration;
}
