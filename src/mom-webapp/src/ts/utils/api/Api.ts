import { Auth } from "../Auth";
import { Endpoints } from "../navigation/Endpoints";
import { Routing } from "../navigation/Routing";
import { Notification, Notifications, NotificationsClass, NotificationsType } from "../Notifications";

export interface ApiResponse<T> {
  ok: boolean;
  error?: string;
  result?: T;
}

export interface ApiCallOptions<I> {
  method: "GET" | "POST";
  endpoint: string;
  body: I;
}

export class Api {
  static async call<I, O>(options: ApiCallOptions<I>): Promise<O> {
    const url = "/api/" + options.endpoint;
    // The session travels as an HttpOnly cookie, sent by the browser on these same-origin calls.
    const body = options.body;

    let response: Response;
    let payload: ApiResponse<O>;
    try {
      response = options.method === "GET"
        ? await fetch(url + "?input=" + encodeURIComponent(JSON.stringify(body)))
        : await fetch(url, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(body),
          });
      payload = await response.json();
    } catch (e) {
      // fetch() itself rejected (offline, DNS, CORS, the server down) or the response wasn't
      // valid JSON (e.g. ApiServlet returning a bodyless 404 for an unknown endpoint) — the
      // server never actually answered. Handled once, here, so every page gets the same
      // notification without needing its own copy. Distinct from the structured {ok:false} case
      // below: there, the server *did* answer, just with a business error or a 401.
      Notifications.addNotification(new Notification(NotificationsClass.SERVER_UNREACHABLE, NotificationsType.ERROR, "Failed to contact server", 5));
      throw e instanceof Error ? e : new Error("Failed to contact server");
    }

    if (!payload.ok) {
      const message = payload.error ?? ("API call to " + options.endpoint + " failed.");
      // A 401 means AbstractAuthenticatedEndpoint rejected the session (expired, or a restart
      // wiped it) — handled once, here, rather than trusting every call site to notice and react:
      // drop the stale session, tell the user why, and bounce to /login, regardless of which page or
      // action triggered the call.
      if (response.status === 401) {
        Auth.clearSession();
        Notifications.addNotification(new Notification(NotificationsClass.SESSION_EXPIRED, NotificationsType.WARNING, "Your session expired.", 6));
        Routing.goToScreen(Endpoints.LOGIN, true);
      }
      // A 403 means the session is fine but the web login's password is still temporary.
      if (response.status === 403) {
        Auth.setPasswordChangeRequired(true);
        Routing.goToScreen(Endpoints.CHANGE_PASSWORD, true);
      }
      throw new Error(message);
    }
    return payload.result as O;
  }
}
