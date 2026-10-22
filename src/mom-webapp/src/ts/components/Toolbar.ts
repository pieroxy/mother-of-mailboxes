import m, { Children } from 'mithril';
import { Logo } from './atoms/icons/Logo';
import { RefreshIcon } from './atoms/icons/RefreshIcon';
import { ProfileIcon } from './atoms/icons/ProfileIcon';
import { SettingsIcon } from './atoms/icons/SettingsIcon';
import { LogsIcon } from './atoms/icons/LogsIcon';
import { Endpoints } from '../utils/navigation/Endpoints';
import { Routing } from '../utils/navigation/Routing';
import { IconButton } from './IconButton';

export class Toolbar implements m.ClassComponent<ToolbarAttrs> {
  view({attrs}:m.Vnode<ToolbarAttrs>): void | Children {
    const current = m.route.get().split("?")[0];
    const link = (target: Endpoints, title: string, icon: m.ComponentTypes) => m(IconButton, {
      icon, size: "xlarge", selected: current === target,
      title: current === target ? title + " (current page)" : title,
      onclick: () => Routing.goToScreen(target),
    });
    return m(".topbar", [
      m("a.logo", {title: "Home", onclick:()=>{Routing.goToScreen(Endpoints.HOME)}}, m(Logo)),
      attrs.refreshData ? m(IconButton, { icon: RefreshIcon, size: "xlarge", title: "Refresh", onclick: attrs.refreshData }) : null,
      m("", {style:{flex:1}}),
      link(Endpoints.APP_LOGS, "Logs", LogsIcon),
      link(Endpoints.GENERAL_SETTINGS, "General settings", SettingsIcon),
      link(Endpoints.PROFILE, "Profile", ProfileIcon),
    ])
  }
}

interface ToolbarAttrs {
  refreshData?:() => void;
}