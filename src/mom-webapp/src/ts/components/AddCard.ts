import m from "mithril";
import { PlusIcon } from "./atoms/icons/PlusIcon";

interface AddCardAttrs {
  label: string;
  title: string;
  onclick: () => void;
  /** "column": icon above the label, for a row of cards. "row" (default): icon beside it, for a vertical list. */
  layout?: "row" | "column";
}

/** A dashed placeholder card at the end of a list, that adds an item to it. */
export class AddCard implements m.ClassComponent<AddCardAttrs> {
  view({ attrs }: m.Vnode<AddCardAttrs>): m.Children {
    return m("a.add-card.layout-" + (attrs.layout ?? "row"), { title: attrs.title, onclick: attrs.onclick }, [
      m(PlusIcon),
      m(".add-card-label", attrs.label),
    ]);
  }
}
