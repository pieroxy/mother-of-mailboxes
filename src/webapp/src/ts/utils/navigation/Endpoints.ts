export enum Endpoints {
  LOGIN = "/login",
  PROFILE = "/profile",
  HOME = "/",
  GENERAL_SETTINGS = "/general-settings",
  STATS = "/stats/:accountName",
  ACCOUNT_SETTINGS = "/settings/:accountName",
  ACCOUNT_CONFIG_EDIT = "/settings/:accountName/config/edit",
  ACCOUNT_CREDENTIALS_EDIT = "/settings/:accountName/credentials/edit",
  RULE_EDIT = "/settings/:accountName/rules/:ruleIndex/edit",
  SHORTCUT_EDIT = "/settings/:accountName/shortcuts/:shortcutIndex/edit",
}
