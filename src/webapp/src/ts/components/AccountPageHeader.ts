import m from "mithril";
import { Routing } from "../utils/navigation/Routing";
import { Endpoints } from "../utils/navigation/Endpoints";
import { SettingsIcon } from "./atoms/icons/SettingsIcon";
import { StatsIcon } from "./atoms/icons/StatsIcon";
import { LogsIcon } from "./atoms/icons/LogsIcon";

export type AccountPage = "settings" | "stats" | "logs";

interface AccountPageHeaderAttrs {
  accountName: string;
  /** The page showing this header: its own icon is left out. */
  current: AccountPage;
}

const LINKS: { page: AccountPage; title: string; icon: m.ComponentTypes; go: (accountName: string) => void }[] = [
  { page: "settings", title: "Account settings", icon: SettingsIcon, go: (accountName) => Routing.goToAccountSettings(accountName) },
  { page: "stats", title: "View stats", icon: StatsIcon, go: (accountName) => Routing.goToStats(accountName) },
  { page: "logs", title: "View decision log", icon: LogsIcon, go: (accountName) => Routing.goToLogs(accountName) },
];

/** Header shared by an account's settings, stats and decision log pages, linking to the other two (same icons as the home page's account card). */
export class AccountPageHeader implements m.ClassComponent<AccountPageHeaderAttrs> {
  view({ attrs }: m.Vnode<AccountPageHeaderAttrs>): m.Children {
    return m(".page-header", [
      m("a.page-back", { onclick: () => Routing.goToScreen(Endpoints.HOME) }, "‹ Back home"),
      m("h1.page-title", attrs.accountName),
      m(".page-header-links", LINKS.filter((link) => link.page !== attrs.current).map((link) =>
        m("span.page-header-link", { title: link.title, onclick: () => link.go(attrs.accountName) }, m(link.icon)))),
    ]);
  }
}
