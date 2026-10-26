import m from "mithril";
import { ApiEndpoints } from "../../auto/ApiEndpoints";
import { SessionDto } from "../../auto/pieroxy-mom";
import { Auth } from "../../utils/Auth";
import { Endpoints } from "../../utils/navigation/Endpoints";
import { Routing } from "../../utils/navigation/Routing";
import { formatDuration } from "../../utils/format";
import { AbstractPage } from "./AbstractPage";

export class ProfilePage extends AbstractPage {
  private sessions: SessionDto[] | undefined;
  private error: string | undefined;

  getPageTitle(): string {
    return "MOM";
  }

  oninit() {
    this.refreshData = () => this.load();
    this.load();
  }

  render(): m.Children {
    const hasOtherSessions = this.sessions !== undefined && this.sessions.some((s) => !s.current);
    return m("page.profilepage", [
      m(".profile-actions", [
        m("button.change-password-button", { onclick: () => Routing.goToScreen(Endpoints.CHANGE_PASSWORD) }, "Change password"),
        m("button.logout-button", { onclick: () => this.logout() }, "Log out"),
        hasOtherSessions ? m("button.secondary", { onclick: () => this.revokeOthers() }, "Log out other sessions") : null,
      ]),
      m(".page-card", [
        m("h2", "Open sessions"),
        this.error ? m(".errorMessage", this.error) : null,
        this.sessions === undefined ? m(".page-loading", "Loading…") : this.renderSessions(this.sessions),
      ]),
    ]);
  }

  private renderSessions(sessions: SessionDto[]): m.Children {
    return m(".sessions-table-wrapper", m("table.sessions-table", [
      m("thead", m("tr", [
        m("th", "Started"), m("th", "Address"), m("th", "Requests"), m("th", "Last request"), m("th", "User agent"), m("th"),
      ])),
      m("tbody", sessions.map((s) => m("tr" + (s.current ? ".current" : ""), { key: s.publicId }, [
        m("td", { title: "Expires " + new Date(s.expiry).toLocaleString() }, new Date(s.start).toLocaleString()),
        m("td", s.lastAddress !== s.loginAddress ? { title: "Logged in from " + s.loginAddress } : {}, s.lastAddress),
        m("td", s.requests.toLocaleString()),
        m("td", formatDuration(s.secondsSinceLastRequest) + " ago"),
        m("td.sessions-user-agent", s.userAgent ?? "—"),
        m("td", s.current
          ? m("span.current-session-badge", { title: "The session you're using right now" }, "This session")
          : m("button.secondary.revoke-session-button", { onclick: () => this.revoke(s) }, "Revoke")),
      ]))),
    ]));
  }

  private load(): Promise<void> {
    return ApiEndpoints.ActiveSessions.call({})
      .then((output) => {
        this.sessions = output.sessions;
        this.error = undefined;
        m.redraw();
      })
      .catch((err: Error) => {
        this.error = err.message;
        m.redraw();
      });
  }

  private revoke(session: SessionDto) {
    this.reloadAfter(ApiEndpoints.RevokeSession.call({ publicId: session.publicId }));
  }

  private revokeOthers() {
    this.reloadAfter(ApiEndpoints.RevokeOtherSessions.call({}));
  }

  /** Reloads either way: a failed revoke usually means the list is stale. Its error outlives the reload. */
  private reloadAfter(revocation: Promise<unknown>) {
    revocation.then(() => this.load(), (err: Error) => this.load().then(() => {
      this.error = err.message;
      m.redraw();
    }));
  }

  private logout() {
    Auth.clearSession();
    Routing.goToScreen(Endpoints.LOGIN);
    ApiEndpoints.Logout.call({});
  }
}
