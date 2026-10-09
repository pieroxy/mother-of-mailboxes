import "../css/style.scss";
import m from "mithril";
import { HomePage } from "./components/pages/HomePage";
import { LoginPage } from "./components/pages/LoginPage";
import { AuthenticatedPageResolver } from "./utils/AuthenticatedPageResolver";
import { Endpoints } from "./utils/navigation/Endpoints";
import { ProfilePage } from "./components/pages/ProfilePage";
import { StatsPage } from "./components/pages/StatsPage";
import { AccountSettingsPage } from "./components/pages/AccountSettingsPage";
import { AccountGeneralEditPage } from "./components/pages/AccountGeneralEditPage";
import { AccountImapSettingsEditPage } from "./components/pages/AccountImapSettingsEditPage";
import { RuleEditPage } from "./components/pages/RuleEditPage";
import { ShortcutEditPage } from "./components/pages/ShortcutEditPage";
import { GeneralSettingsPage } from "./components/pages/GeneralSettingsPage";
import { ChangePasswordPage } from "./components/pages/ChangePasswordPage";
import { AccountCreatePage } from "./components/pages/AccountCreatePage";
import { SetupWizardPage } from "./components/pages/SetupWizardPage";

const routes: m.RouteDefs = {
  [Endpoints.LOGIN]: LoginPage,
  [Endpoints.CHANGE_PASSWORD]: new AuthenticatedPageResolver(ChangePasswordPage, "passwordChange"),
  [Endpoints.SETUP]: new AuthenticatedPageResolver(SetupWizardPage, "setup"),
  [Endpoints.HOME]: new AuthenticatedPageResolver(HomePage),
  [Endpoints.PROFILE]: new AuthenticatedPageResolver(ProfilePage),
  [Endpoints.GENERAL_SETTINGS]: new AuthenticatedPageResolver(GeneralSettingsPage),
  [Endpoints.ACCOUNT_CREATE]: new AuthenticatedPageResolver(AccountCreatePage),
  [Endpoints.STATS]: new AuthenticatedPageResolver(StatsPage),
  [Endpoints.ACCOUNT_SETTINGS]: new AuthenticatedPageResolver(AccountSettingsPage),
  [Endpoints.ACCOUNT_GENERAL_EDIT]: new AuthenticatedPageResolver(AccountGeneralEditPage),
  [Endpoints.ACCOUNT_IMAP_SETTINGS_EDIT]: new AuthenticatedPageResolver(AccountImapSettingsEditPage),
  [Endpoints.RULE_EDIT]: new AuthenticatedPageResolver(RuleEditPage),
  [Endpoints.SHORTCUT_EDIT]: new AuthenticatedPageResolver(ShortcutEditPage),
};

m.route(document.getElementById("app")!, Endpoints.LOGIN, routes);
