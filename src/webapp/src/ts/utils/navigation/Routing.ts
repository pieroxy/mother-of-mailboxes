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
}
