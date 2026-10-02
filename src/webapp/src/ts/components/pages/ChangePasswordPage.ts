import m from "mithril";
import { ApiEndpoints } from "../../auto/ApiEndpoints";
import { LogoSub } from "../atoms/icons/Logo";
import { Auth, AuthStatus } from "../../utils/Auth";
import { Endpoints } from "../../utils/navigation/Endpoints";
import { Routing } from "../../utils/navigation/Routing";
import { AbstractPage } from "./AbstractPage";
import { Notification, Notifications, NotificationsClass, NotificationsType } from "../../utils/Notifications";

/**
 * Reached from the profile page, or forced right after logging in with a temporary password — in
 * which case nothing else is reachable until it's replaced, hence no toolbar and no cancel.
 */
export class ChangePasswordPage extends AbstractPage {
  private newPassword = ""
  private confirmation = ""
  private submitting = false
  private error: string | undefined

  showToolbar(): boolean {
    return !this.isForced()
  }

  getPageTitle(): string {
    return "MOM - Change password"
  }

  render(): m.Children {
    return m("page.changepasswordpage", [
      m(LogoSub),
      m("form", { onsubmit: (e: Event) => this.submit(e) }, [
        this.isForced() ? m("p", "Your password is temporary. Choose a new one to continue.") : null,
        m("input", {
          type: "password",
          placeholder: "New password",
          autocomplete: "new-password",
          value: this.newPassword,
          disabled: this.submitting,
          oninput: (e: Event) => (this.newPassword = (e.target as HTMLInputElement).value),
        }),
        m("input", {
          type: "password",
          placeholder: "Confirm new password",
          autocomplete: "new-password",
          value: this.confirmation,
          disabled: this.submitting,
          oninput: (e: Event) => (this.confirmation = (e.target as HTMLInputElement).value),
        }),
        m("button", { type: "submit", disabled: this.submitting }, "Change password"),
        this.isForced() ? null : m("button.secondary", {
          type: "button", disabled: this.submitting, onclick: () => Routing.goToScreen(Endpoints.PROFILE),
        }, "Cancel"),
        this.error ? m("div.errorMessage", this.error) : null,
      ])
    ])
  }

  private isForced(): boolean {
    return Auth.getStatus() === AuthStatus.PASSWORD_CHANGE_REQUIRED
  }

  private submit(e: Event) {
    e.preventDefault();
    if (!this.newPassword.trim()) {
      this.error = "The new password must not be blank.";
      return;
    }
    if (this.newPassword !== this.confirmation) {
      this.error = "The two passwords don't match.";
      return;
    }
    this.submitting = true;
    this.error = undefined;
    ApiEndpoints.ChangePassword.call({ newPassword: this.newPassword })
      .then(() => {
        this.submitting = false;
        const wasForced = this.isForced();
        Auth.setPasswordChangeRequired(false);
        Notifications.addNotification(new Notification(NotificationsClass.PASSWORD_CHANGED, NotificationsType.SUCCESS, "Password changed.", 4));
        Routing.goToScreen(wasForced ? Endpoints.HOME : Endpoints.PROFILE, true);
        m.redraw();
      })
      .catch((err: Error) => {
        this.submitting = false;
        this.error = err.message;
        m.redraw();
      });
  }
}
