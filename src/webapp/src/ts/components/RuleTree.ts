import m from "mithril";
import { MailFilterRuleActionConfiguration, MailFilterRuleMatcherConfiguration } from "../auto/pieroxy-mom";

/** Read-only recursive matcher/action tree — shared by AccountSettingsPage (viewing) and RuleEditPage (viewing a composite it can't yet edit). */

export function renderMatcherNode(node: MailFilterRuleMatcherConfiguration): m.Children {
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

export function renderActionNode(node: MailFilterRuleActionConfiguration): m.Children {
  return m("li.tree-node", [
    m(".tree-node-label", [
      m("span.tree-node-type", node.type),
      node.key ? m("span.tree-node-detail", ": " + node.key) : null,
    ]),
    node.children && node.children.length > 0 ? m("ul.tree-children", node.children.map(renderActionNode)) : null,
  ]);
}
