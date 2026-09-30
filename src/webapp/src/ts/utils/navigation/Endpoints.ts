export enum Endpoints {
  LOGIN = "/login",
  PROFILE = "/profile",
  HOME = "/",
  GENERAL_SETTINGS = "/general-settings",
  STATS = "/stats/:accountName",
  ACCOUNT_SETTINGS = "/settings/:accountName",
  ACCOUNT_GENERAL_EDIT = "/settings/:accountName/general/edit",
  ACCOUNT_IMAP_SETTINGS_EDIT = "/settings/:accountName/imap-settings/edit",
  RULE_EDIT = "/settings/:accountName/rules/:ruleIndex/edit",
  SHORTCUT_EDIT = "/settings/:accountName/shortcuts/:shortcutIndex/edit",
}
