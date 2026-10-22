import m from "mithril";
import { RefreshIcon } from "./atoms/icons/RefreshIcon";

interface RefreshActionAttrs {
  label: string;
  busyLabel: string;
  title: string;
  busy: boolean;
  onclick: () => void;
}

/** The refresh icon with a tiny label below; spins and ignores clicks while busy. */
export class RefreshAction implements m.ClassComponent<RefreshActionAttrs> {
  view({ attrs }: m.Vnode<RefreshActionAttrs>): m.Children {
    return m(".refresh-action" + (attrs.busy ? ".busy" : ""), {
      title: attrs.title,
      onclick: () => {
        if (!attrs.busy) attrs.onclick();
      },
    }, [
      m(RefreshIcon),
      m(".refresh-action-label", attrs.busy ? attrs.busyLabel : attrs.label),
    ]);
  }
}
