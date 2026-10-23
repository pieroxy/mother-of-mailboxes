import "../css/style.scss";
import m from "mithril";
import { HomePage } from "./components/pages/HomePage";
import { LoginPage } from "./components/pages/LoginPage";
import { AuthenticatedPageResolver } from "./utils/AuthenticatedPageResolver";
import { Endpoints } from "./utils/navigation/Endpoints";
import { ProfilePage } from "./components/pages/ProfilePage";
import { StatsPage } from "./components/pages/StatsPage";
import { LogsPage } from "./components/pages/LogsPage";
import { AccountSettingsPage } from "./components/pages/AccountSettingsPage";
import { AccountGeneralEditPage } from "./components/pages/AccountGeneralEditPage";
import { AccountImapSettingsEditPage } from "./components/pages/AccountImapSettingsEditPage";
import { AccountSmtpSettingsEditPage } from "./components/pages/AccountSmtpSettingsEditPage";
import { RuleEditPage } from "./components/pages/RuleEditPage";
import { ShortcutEditPage } from "./components/pages/ShortcutEditPage";
import { GeneralSettingsPage } from "./components/pages/GeneralSettingsPage";
import { AppLogsPage } from "./components/pages/AppLogsPage";
import { ChangePasswordPage } from "./components/pages/ChangePasswordPage";
import { AccountCreatePage } from "./components/pages/AccountCreatePage";
import { SetupWizardPage } from "./components/pages/SetupWizardPage";
import { AccountHomePage } from "./components/pages/AccountHomePage";
import { DeliveryCheckPage } from "./components/pages/DeliveryCheckPage";
import { NotificationHost } from "./components/NotificationComponent";
import { CreditsPage } from "./components/pages/CreditsPage";

const routes: m.RouteDefs = {
  [Endpoints.LOGIN]: LoginPage,
  [Endpoints.CREDITS]: CreditsPage,
  [Endpoints.CHANGE_PASSWORD]: new AuthenticatedPageResolver(ChangePasswordPage, "passwordChange"),
  [Endpoints.SETUP]: new AuthenticatedPageResolver(SetupWizardPage, "setup"),
  [Endpoints.HOME]: new AuthenticatedPageResolver(HomePage),
  [Endpoints.PROFILE]: new AuthenticatedPageResolver(ProfilePage),
  [Endpoints.GENERAL_SETTINGS]: new AuthenticatedPageResolver(GeneralSettingsPage),
  [Endpoints.APP_LOGS]: new AuthenticatedPageResolver(AppLogsPage),
  [Endpoints.ACCOUNT_CREATE]: new AuthenticatedPageResolver(AccountCreatePage),
  [Endpoints.ACCOUNT_HOME]: new AuthenticatedPageResolver(AccountHomePage),
  [Endpoints.DELIVERY_CHECK]: new AuthenticatedPageResolver(DeliveryCheckPage),
  [Endpoints.STATS]: new AuthenticatedPageResolver(StatsPage),
  [Endpoints.LOGS]: new AuthenticatedPageResolver(LogsPage),
  [Endpoints.ACCOUNT_SETTINGS]: new AuthenticatedPageResolver(AccountSettingsPage),
  [Endpoints.ACCOUNT_GENERAL_EDIT]: new AuthenticatedPageResolver(AccountGeneralEditPage),
  [Endpoints.ACCOUNT_IMAP_SETTINGS_EDIT]: new AuthenticatedPageResolver(AccountImapSettingsEditPage),
  [Endpoints.ACCOUNT_SMTP_SETTINGS_EDIT]: new AuthenticatedPageResolver(AccountSmtpSettingsEditPage),
  [Endpoints.RULE_EDIT]: new AuthenticatedPageResolver(RuleEditPage),
  [Endpoints.SHORTCUT_EDIT]: new AuthenticatedPageResolver(ShortcutEditPage),
};

m.route(document.getElementById("app")!, Endpoints.LOGIN, routes);
// Outside the routed tree, so a page change doesn't re-create the notification showing.
m.mount(document.getElementById("notifications")!, NotificationHost);
