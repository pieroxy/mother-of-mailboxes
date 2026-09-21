import m from "mithril";
import { Endpoints } from "./Endpoints";

export class Routing {
  static goToScreen(target: Endpoints, replace: boolean = false) {
    m.route.set(target, null, { replace });
  }
}
