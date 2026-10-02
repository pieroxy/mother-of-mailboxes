import m from "mithril";
import { ApiEndpoints } from "../../auto/ApiEndpoints";
import { LogoSub } from "../atoms/icons/Logo";
import { Auth } from "../../utils/Auth";
import { Endpoints } from "../../utils/navigation/Endpoints";
import { Routing } from "../../utils/navigation/Routing";
import { AbstractPage } from "./AbstractPage";

/** Shown right after logging in with a temporary password; nothing else is reachable until it's replaced. */
export class ChangePasswordPage extends AbstractPage {
  private newPassword = ""
  private confirmation = ""
  private submitting = false
  private error: string | undefined

  showToolbar(): boolean {
    return false
  }

  getPageTitle(): string {
    return "MOM - Change password"
  }

  render(): m.Children {
    return m("page.changepasswordpage", [
      m(LogoSub),
      m("form", { onsubmit: (e: Event) => this.submit(e) }, [
        m("p", "Your password is temporary. Choose a new one to continue."),
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
        this.error ? m("div.errorMessage", this.error) : null,
      ])
    ])
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
        Auth.setPasswordChangeRequired(false);
        Routing.goToScreen(Endpoints.HOME, true);
        m.redraw();
      })
      .catch((err: Error) => {
        this.submitting = false;
        this.error = err.message;
        m.redraw();
      });
  }
}
