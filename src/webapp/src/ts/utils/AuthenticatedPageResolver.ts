import m from "mithril";
import { Auth, AuthStatus } from "./Auth";
import { Endpoints } from "./navigation/Endpoints";
import { Routing } from "./navigation/Routing";

/**
 * Wraps a page component that requires a logged-in session, redirecting to /login otherwise.
 * Hands mithril a small live-checking wrapper rather than the target component directly:
 * Auth.getStatus() is async while a stored session ID is being validated (see Auth.ts), and only
 * triggers a plain m.redraw() once it resolves — which re-runs this wrapper's view() but does
 * *not* re-invoke onmatch() (mithril only does that on an actual route change). Returning the
 * target component straight from onmatch() would therefore never notice a CHECKING -> LOGGED_IN
 * (or CHECKING -> NO_SESSION, for an expired/invalid stored session) transition. The redirect
 * check lives in view(), not oninit(), for the same reason: it must re-run on that later redraw,
 * not just once at mount time. While the password is temporary, every page but the
 * change-password one (isPasswordChangePage) redirects there.
 */
export class AuthenticatedPageResolver implements m.RouteResolver {
  private readonly component: m.ComponentTypes<any, any>;
  private readonly isPasswordChangePage: boolean;

  constructor(component: m.ComponentTypes<any, any>, isPasswordChangePage: boolean = false) {
    this.component = component;
    this.isPasswordChangePage = isPasswordChangePage;
  }

  onmatch(): m.ComponentTypes<any, any> {
    const wrapped = this.component;
    const isPasswordChangePage = this.isPasswordChangePage;
    return {
      view(vnode: m.Vnode) {
        const status = Auth.getStatus();
        if (status === AuthStatus.LOGGED_IN) {
          return m(wrapped, vnode.attrs);
        }
        if (status === AuthStatus.PASSWORD_CHANGE_REQUIRED) {
          if (isPasswordChangePage) return m(wrapped, vnode.attrs);
          Routing.goToScreen(Endpoints.CHANGE_PASSWORD, true);
          return null;
        }
        if (status === AuthStatus.NO_SESSION) {
          Routing.goToScreen(Endpoints.LOGIN, true);
        }
        return null; // CHECKING, or NO_SESSION with the redirect above already under way
      },
    };
  }
}
