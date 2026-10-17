import m from "mithril";
import { ApiEndpoints } from "../auto/ApiEndpoints";

// Where the session ID used to be kept, before the session cookie.
try {
  localStorage.removeItem("mom.sessionId");
} catch {
  // Storage unavailable: nothing to clean up.
}

export enum AuthStatus {
  LOGGED_IN,
  /** Logged in, but every API call except ChangePassword is refused until the temporary password is replaced. */
  PASSWORD_CHANGE_REQUIRED,
  NO_SESSION,
  CHECKING,
}

/**
 * Client-side session state. The session itself is an HttpOnly cookie this code can't see: on
 * page load, SessionApi tells whether it's (still) valid — a service restart clears every session.
 */
export class Auth {
  /** Undefined until SessionApi has answered. */
  private static loggedIn: boolean | undefined;
  private static passwordChangeRequired = false;
  private static setupInProgress = false;
  private static checkInProgress = false;

  static getStatus(): AuthStatus {
    if (Auth.loggedIn === true) return Auth.passwordChangeRequired ? AuthStatus.PASSWORD_CHANGE_REQUIRED : AuthStatus.LOGGED_IN;
    if (Auth.loggedIn === false) return AuthStatus.NO_SESSION;

    if (!Auth.checkInProgress) {
      Auth.checkInProgress = true;
      ApiEndpoints.Session.call({})
        .then((output) => {
          Auth.checkInProgress = false;
          Auth.loggedIn = output.authenticated;
          Auth.passwordChangeRequired = output.passwordChangeRequired;
          Auth.setupInProgress = output.setupInProgress;
          m.redraw();
        })
        .catch(() => {
          Auth.checkInProgress = false;
          m.redraw();
        });
    }
    return AuthStatus.CHECKING;
  }

  static setLoggedIn(passwordChangeRequired: boolean, setupInProgress: boolean) {
    Auth.loggedIn = true;
    Auth.passwordChangeRequired = passwordChangeRequired;
    Auth.setupInProgress = setupInProgress;
  }

  /** The setup wizard isn't finished: every page but it redirects there (see AuthenticatedPageResolver). */
  static isSetupInProgress(): boolean {
    return Auth.setupInProgress;
  }

  static setSetupInProgress(setupInProgress: boolean) {
    Auth.setupInProgress = setupInProgress;
  }

  static setPasswordChangeRequired(passwordChangeRequired: boolean) {
    Auth.passwordChangeRequired = passwordChangeRequired;
  }

  static clearSession() {
    Auth.loggedIn = false;
    Auth.passwordChangeRequired = false;
    Auth.setupInProgress = false;
  }
}
