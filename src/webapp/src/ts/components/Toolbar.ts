import m, { Children } from 'mithril';
import { Logo } from './atoms/icons/Logo';
import { RefreshIcon } from './atoms/icons/RefreshIcon';

export class Toolbar implements m.ClassComponent<ToolbarAttrs> {
  view({attrs}:m.Vnode<ToolbarAttrs>): void | Children {
    return m(".topbar", [
      m("span.logo", m(Logo)),
      attrs.refreshData ? m("span.refresh", { onclick: attrs.refreshData }, m(RefreshIcon)) : null
    ])
  }
}

interface ToolbarAttrs {
  refreshData?:() => void;
}