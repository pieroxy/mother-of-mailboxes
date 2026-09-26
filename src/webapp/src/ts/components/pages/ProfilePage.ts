import m from "mithril";
import { ApiEndpoints } from "../../auto/ApiEndpoints";
import { Auth } from "../../utils/Auth";
import { Endpoints } from "../../utils/navigation/Endpoints";
import { Routing } from "../../utils/navigation/Routing";
import { AbstractPage } from "./AbstractPage";

export class ProfilePage extends AbstractPage {

  getPageTitle(): string {
    return "MOM";
  }

  render(): m.Children {
    return m("page.profilepage", [
      m("button.logout-button", { onclick: () => this.logout() }, "Log out"),
    ]);
  }

  private logout() {
    const sessionId = Auth.clearSession();
    Routing.goToScreen(Endpoints.LOGIN);
    if (sessionId) ApiEndpoints.Logout.call({ sessionId });
  }
}