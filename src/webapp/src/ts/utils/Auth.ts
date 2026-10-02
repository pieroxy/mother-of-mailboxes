import m from "mithril";
import { ApiEndpoints } from "../auto/ApiEndpoints";

const STORAGE_KEY = "mom.sessionId";

export enum AuthStatus {
  LOGGED_IN,
  /** Logged in, but every API call except ChangePassword is refused until the temporary password is replaced. */
  PASSWORD_CHANGE_REQUIRED,
  NO_SESSION,
  CHECKING,
}

/**
 * Client-side session state. The session ID itself lives in localStorage so a page reload
 * doesn't force a fresh login — but that ID is only ever a claim until SessionApi confirms it's
 * still valid server-side (see SessionStore: a service restart clears every session, so a stale
 * ID left over in localStorage from before a restart must be rejected, not trusted blindly).
 */
export class Auth {
  private static sessionId: string | undefined;
  private static passwordChangeRequired = false;
  private static checkInProgress = false;

  static getStatus(): AuthStatus {
    if (Auth.sessionId) return Auth.passwordChangeRequired ? AuthStatus.PASSWORD_CHANGE_REQUIRED : AuthStatus.LOGGED_IN;

    const stored = localStorage.getItem(STORAGE_KEY);
    if (!stored) return AuthStatus.NO_SESSION;

    if (!Auth.checkInProgress) {
      Auth.checkInProgress = true;
      ApiEndpoints.Session.call({ sessionId: stored })
        .then((output) => {
          Auth.checkInProgress = false;
          if (output.authenticated) {
            Auth.sessionId = stored;
            Auth.passwordChangeRequired = output.passwordChangeRequired;
          } else {
            localStorage.removeItem(STORAGE_KEY);
          }
          m.redraw();
        })
        .catch(() => {
          Auth.checkInProgress = false;
          m.redraw();
        });
    }
    return AuthStatus.CHECKING;
  }

  static setSession(sessionId: string, passwordChangeRequired: boolean) {
    Auth.sessionId = sessionId;
    Auth.passwordChangeRequired = passwordChangeRequired;
    localStorage.setItem(STORAGE_KEY, sessionId);
  }

  static setPasswordChangeRequired(passwordChangeRequired: boolean) {
    Auth.passwordChangeRequired = passwordChangeRequired;
  }

  /** @return the session ID that was active, if any (e.g. so the caller can tell the server to invalidate it). */
  static clearSession(): string | undefined {
    const sessionId = Auth.sessionId;
    Auth.sessionId = undefined;
    Auth.passwordChangeRequired = false;
    localStorage.removeItem(STORAGE_KEY);
    return sessionId;
  }

  static getSessionId(): string | undefined {
    return Auth.sessionId;
  }
}
