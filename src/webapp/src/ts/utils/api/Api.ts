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
    // Every authenticated endpoint's input carries a sessionId (see AuthenticatedApiInput) —
    // attached here, once, rather than by every call site, so nothing can forget it. Login's
    // input has no such field, so this is a no-op for it; Session/Logout each pass their own
    // explicit sessionId (deliberately not always Auth.getSessionId() — e.g. Session validates a
    // freshly-read localStorage value *before* Auth.sessionId is set at all), which this must
    // never clobber.
    const body: Record<string, unknown> = { ...(options.body as Record<string, unknown>) };
    if (body.sessionId === undefined) {
      body.sessionId = Auth.getSessionId();
    }

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
      // drop the stale ID, tell the user why, and bounce to /login, regardless of which page or
      // action triggered the call.
      if (response.status === 401) {
        Auth.clearSession();
        Notifications.addNotification(new Notification(NotificationsClass.SESSION_EXPIRED, NotificationsType.WARNING, "Your session expired.", 6));
        Routing.goToScreen(Endpoints.LOGIN, true);
      }
      throw new Error(message);
    }
    return payload.result as O;
  }
}
