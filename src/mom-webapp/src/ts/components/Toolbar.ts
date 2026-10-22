import m, { Children } from 'mithril';
import { Logo } from './atoms/icons/Logo';
import { RefreshIcon } from './atoms/icons/RefreshIcon';
import { ProfileIcon } from './atoms/icons/ProfileIcon';
import { SettingsIcon } from './atoms/icons/SettingsIcon';
import { LogsIcon } from './atoms/icons/LogsIcon';
import { HomeIcon } from './atoms/icons/HomeIcon';
import { Endpoints } from '../utils/navigation/Endpoints';
import { Routing } from '../utils/navigation/Routing';
import { IconButton } from './IconButton';

// Shortest spin of the refresh icon, so that an instant refresh still visibly happens.
const MIN_REFRESH_SPIN_MS = 500;

export class Toolbar implements m.ClassComponent<ToolbarAttrs> {
  private refreshing = false;

  view({attrs}:m.Vnode<ToolbarAttrs>): void | Children {
    const current = m.route.get().split("?")[0];
    const link = (target: Endpoints, title: string, icon: m.ComponentTypes) => m(IconButton, {
      icon, size: "xlarge", selected: current === target,
      title: current === target ? title + " (current page)" : title,
      onclick: () => Routing.goToScreen(target),
    });
    return m(".topbar", [
      m("a.logo", {title: "Home", onclick:()=>{Routing.goToScreen(Endpoints.HOME)}}, m(Logo)),
      attrs.refreshData ? m(IconButton, {
        icon: RefreshIcon, size: "xlarge", busy: this.refreshing,
        title: this.refreshing ? "Refreshing…" : "Refresh",
        onclick: () => this.refresh(attrs.refreshData!),
      }) : null,
      m("", {style:{flex:1}}),
      link(Endpoints.HOME, "Home", HomeIcon),
      link(Endpoints.APP_LOGS, "Logs", LogsIcon),
      link(Endpoints.GENERAL_SETTINGS, "General settings", SettingsIcon),
      link(Endpoints.PROFILE, "Profile", ProfileIcon),
    ])
  }

  private refresh(refreshData: () => Promise<unknown>) {
    this.refreshing = true;
    const started = Date.now();
    // The page shows its own errors: the spin only has to end.
    refreshData().catch(() => undefined).then(() => {
      window.setTimeout(() => {
        this.refreshing = false;
        m.redraw();
      }, Math.max(0, MIN_REFRESH_SPIN_MS - (Date.now() - started)));
    });
  }
}

interface ToolbarAttrs {
  refreshData?:() => Promise<unknown>;
}