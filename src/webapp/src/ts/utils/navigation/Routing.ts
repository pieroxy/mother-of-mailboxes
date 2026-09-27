import m from "mithril";
import { Endpoints } from "./Endpoints";

export class Routing {
  static goToScreen(target: Endpoints, replace: boolean = false) {
    m.route.set(target, null, { replace });
  }

  static goToStats(accountName: string) {
    m.route.set(Endpoints.STATS, { accountName });
  }

  static goToAccountSettings(accountName: string) {
    m.route.set(Endpoints.ACCOUNT_SETTINGS, { accountName });
  }

  static goToAccountConfigEdit(accountName: string) {
    m.route.set(Endpoints.ACCOUNT_CONFIG_EDIT, { accountName });
  }

  static goToAccountCredentialsEdit(accountName: string) {
    m.route.set(Endpoints.ACCOUNT_CREDENTIALS_EDIT, { accountName });
  }

  /** ruleIndex is the rule's position in the account's rules array, or "new" to create one. */
  static goToRuleEdit(accountName: string, ruleIndex: number | "new") {
    m.route.set(Endpoints.RULE_EDIT, { accountName, ruleIndex });
  }
}
