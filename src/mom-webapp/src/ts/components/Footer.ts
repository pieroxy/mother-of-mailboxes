import m from "mithril";
import { ApiEndpoints } from "../auto/ApiEndpoints";
import { VersionApiOutput } from "../auto/pieroxy-mom";
import { Auth, AuthStatus } from "../utils/Auth";
import { Endpoints } from "../utils/navigation/Endpoints";
import { Routing } from "../utils/navigation/Routing";

const SOURCE_URL = "https://github.com/pieroxy/mother-of-mailboxes";

/** Fetched once per page load, and only with a session: the version is never shown before login. */
let version: VersionApiOutput | undefined;
let versionRequested = false;

/** Shown on every page, the login page included. */
export class Footer implements m.ClassComponent {
  view(): m.Children {
    const status = Auth.getStatus();
    const loggedIn = status === AuthStatus.LOGGED_IN || status === AuthStatus.PASSWORD_CHANGE_REQUIRED;
    if (loggedIn && !versionRequested) {
      versionRequested = true;
      ApiEndpoints.Version.call({})
        .then((output) => {
          version = output;
          m.redraw();
        })
        .catch(() => undefined);
    }
    return m("footer.app-footer", [
      loggedIn && version ? m("span", { title: "Revision " + version.revision }, "MOM " + version.version) : null,
      m("a", { href: SOURCE_URL, target: "_blank", rel: "noopener" }, "Source on GitHub"),
      m("a", { onclick: () => Routing.goToScreen(Endpoints.CREDITS) }, "Credits"),
    ]);
  }
}
