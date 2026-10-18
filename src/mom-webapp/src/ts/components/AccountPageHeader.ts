import m from "mithril";
import { Routing } from "../utils/navigation/Routing";
import { Endpoints } from "../utils/navigation/Endpoints";
import { SettingsIcon } from "./atoms/icons/SettingsIcon";
import { StatsIcon } from "./atoms/icons/StatsIcon";
import { LogsIcon } from "./atoms/icons/LogsIcon";

export type AccountPage = "home" | "settings" | "stats" | "logs" | "delivery-check";

interface AccountPageHeaderAttrs {
  accountName: string;
  /** The page showing this header: its own icon is left out. The account home page itself shows none, since it already links to all of them. */
  current: AccountPage;
}

const LINKS: { page: AccountPage; title: string; icon: m.ComponentTypes; go: (accountName: string) => void }[] = [
  { page: "settings", title: "Account settings", icon: SettingsIcon, go: (accountName) => Routing.goToAccountSettings(accountName) },
  { page: "stats", title: "View stats", icon: StatsIcon, go: (accountName) => Routing.goToStats(accountName) },
  { page: "logs", title: "View decision log", icon: LogsIcon, go: (accountName) => Routing.goToLogs(accountName) },
];

/**
 * Header shared by an account's pages: a back link (to the account home page, or to the app's
 * home page from the account home page itself) and, on its settings, stats and decision log
 * pages, shortcut icons to the other two.
 */
export class AccountPageHeader implements m.ClassComponent<AccountPageHeaderAttrs> {
  view({ attrs }: m.Vnode<AccountPageHeaderAttrs>): m.Children {
    const isHome = attrs.current === "home";
    return m(".page-header", [
      isHome
        ? m("a.page-back", { onclick: () => Routing.goToScreen(Endpoints.HOME) }, "‹ Back home")
        : m("a.page-back", { onclick: () => Routing.goToAccountHome(attrs.accountName) }, "‹ Back"),
      m("h1.page-title", attrs.accountName),
      isHome ? null : m(".page-header-links", LINKS.filter((link) => link.page !== attrs.current).map((link) =>
        m("span.page-header-link", { title: link.title, onclick: () => link.go(attrs.accountName) }, m(link.icon)))),
    ]);
  }
}
