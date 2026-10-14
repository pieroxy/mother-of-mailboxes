import m from "mithril";
import { Endpoints } from "./Endpoints";

export interface LogsFilters {
  from?: string;
  to?: string;
  /** A StatsApi top-matchers key. */
  matcher?: string;
  matcherByType?: boolean;
  matcherBlockingOnly?: boolean;
}

export class Routing {
  static goToScreen(target: Endpoints, replace: boolean = false) {
    m.route.set(target, null, { replace });
  }

  static goToStats(accountName: string) {
    m.route.set(Endpoints.STATS, { accountName });
  }

  /** filters: initial LogsPage filters, passed as query parameters (see LogsPageAttrs). */
  static goToLogs(accountName: string, filters: LogsFilters = {}) {
    m.route.set(Endpoints.LOGS, { accountName, ...filters });
  }

  static goToAccountSettings(accountName: string) {
    m.route.set(Endpoints.ACCOUNT_SETTINGS, { accountName });
  }

  static goToAccountGeneralEdit(accountName: string) {
    m.route.set(Endpoints.ACCOUNT_GENERAL_EDIT, { accountName });
  }

  static goToAccountImapSettingsEdit(accountName: string) {
    m.route.set(Endpoints.ACCOUNT_IMAP_SETTINGS_EDIT, { accountName });
  }

  static goToAccountSmtpSettingsEdit(accountName: string) {
    m.route.set(Endpoints.ACCOUNT_SMTP_SETTINGS_EDIT, { accountName });
  }

  /** ruleIndex is the rule's position in the account's rules array, or "new" to create one. */
  static goToRuleEdit(accountName: string, ruleIndex: number | "new") {
    m.route.set(Endpoints.RULE_EDIT, { accountName, ruleIndex });
  }

  /** shortcutIndex is the shortcut's position in the account's shortcuts array, or "new" to create one. */
  static goToShortcutEdit(accountName: string, shortcutIndex: number | "new") {
    m.route.set(Endpoints.SHORTCUT_EDIT, { accountName, shortcutIndex });
  }
}
