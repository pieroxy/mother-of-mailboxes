import m, { Children } from 'mithril';
import { Logo } from './atoms/icons/Logo';
import { RefreshIcon } from './atoms/icons/RefreshIcon';
import { ProfileIcon } from './atoms/icons/ProfileIcon';
import { Endpoints } from '../utils/navigation/Endpoints';
import { Routing } from '../utils/navigation/Routing';

export class Toolbar implements m.ClassComponent<ToolbarAttrs> {
  view({attrs}:m.Vnode<ToolbarAttrs>): void | Children {
    return m(".topbar", [
      m("a.logo", {onclick:()=>{Routing.goToScreen(Endpoints.HOME)}}, m(Logo)),
      attrs.refreshData ? m("span.refresh", { onclick: attrs.refreshData }, m(RefreshIcon)) : null,
      m("", {style:{flex:1}}),
      m("a", {onclick:()=>{Routing.goToScreen(Endpoints.PROFILE)}}, m(ProfileIcon))
    ])
  }
}

interface ToolbarAttrs {
  refreshData?:() => void;
}