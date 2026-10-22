import m from "mithril";
import { Routing } from "../utils/navigation/Routing";
import { Endpoints } from "../utils/navigation/Endpoints";
import { SettingsIcon } from "./atoms/icons/SettingsIcon";
import { StatsIcon } from "./atoms/icons/StatsIcon";
import { LogsIcon } from "./atoms/icons/LogsIcon";
import { DocumentCheckIcon } from "./atoms/icons/DocumentCheckIcon";
import { IconButton } from "./IconButton";

export type AccountPage = "home" | "settings" | "stats" | "logs" | "delivery-check";

interface AccountPageHeaderAttrs {
  accountName: string;
  /** The page showing this header: its icon is shown selected. The account home page itself shows none, since it already links to all of them. */
  current: AccountPage;
}

const LINKS: { page: AccountPage; name: string; title: string; icon: m.ComponentTypes; go: (accountName: string) => void }[] = [
  { page: "settings", name: "Account settings", title: "Account settings", icon: SettingsIcon, go: (accountName) => Routing.goToAccountSettings(accountName) },
  { page: "stats", name: "Stats", title: "View stats", icon: StatsIcon, go: (accountName) => Routing.goToStats(accountName) },
  { page: "logs", name: "Decision log", title: "View decision log", icon: LogsIcon, go: (accountName) => Routing.goToLogs(accountName) },
  { page: "delivery-check", name: "Delivery settings check", title: "Check delivery settings", icon: DocumentCheckIcon, go: (accountName) => Routing.goToDeliveryCheck(accountName) },
];

/**
 * Header shared by an account's pages: a back link (to the account home page, or to the app's
 * home page from the account home page itself) and, on every other page, tab-like icons to all
 * of them, the current one selected.
 */
export class AccountPageHeader implements m.ClassComponent<AccountPageHeaderAttrs> {
  view({ attrs }: m.Vnode<AccountPageHeaderAttrs>): m.Children {
    const isHome = attrs.current === "home";
    return m(".page-header", [
      isHome
        ? m("a.page-back", { onclick: () => Routing.goToScreen(Endpoints.HOME) }, "‹ Back home")
        : m("a.page-back", { onclick: () => Routing.goToAccountHome(attrs.accountName) }, "‹ Back"),
      m("h1.page-title", attrs.accountName),
      isHome ? null : m(".page-header-links", LINKS.map((link) => {
        const selected = link.page === attrs.current;
        return m(IconButton, {
          icon: link.icon, size: "large", selected,
          title: selected ? link.name + " (current page)" : link.title,
          onclick: () => link.go(attrs.accountName),
        });
      })),
    ]);
  }
}
