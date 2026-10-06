import m, { Children } from 'mithril';
import { Logo } from './atoms/icons/Logo';
import { RefreshIcon } from './atoms/icons/RefreshIcon';
import { ProfileIcon } from './atoms/icons/ProfileIcon';
import { SettingsIcon } from './atoms/icons/SettingsIcon';
import { Endpoints } from '../utils/navigation/Endpoints';
import { Routing } from '../utils/navigation/Routing';

export class Toolbar implements m.ClassComponent<ToolbarAttrs> {
  view({attrs}:m.Vnode<ToolbarAttrs>): void | Children {
    return m(".topbar", [
      m("a.logo", {title: "Home", onclick:()=>{Routing.goToScreen(Endpoints.HOME)}}, m(Logo)),
      attrs.refreshData ? m("span.refresh", { title: "Refresh", onclick: attrs.refreshData }, m(RefreshIcon)) : null,
      m("", {style:{flex:1}}),
      m("a.general-settings-link", {title: "General settings", onclick:()=>{Routing.goToScreen(Endpoints.GENERAL_SETTINGS)}}, m(SettingsIcon)),
      m("a", {title: "Profile", onclick:()=>{Routing.goToScreen(Endpoints.PROFILE)}}, m(ProfileIcon))
    ])
  }
}

interface ToolbarAttrs {
  refreshData?:() => void;
}