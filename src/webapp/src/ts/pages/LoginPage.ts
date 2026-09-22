import m from "mithril";
import { ApiEndpoints } from "../auto/ApiEndpoints";
import { Auth } from "../utils/Auth";
import { Endpoints } from "../utils/navigation/Endpoints";
import { Routing } from "../utils/navigation/Routing";
import { AbstractPage } from "./AbstractPage";

export class LoginPage extends AbstractPage {
  private login = "";
  private password = "";
  private submitting = false;
  private error: string | undefined;

  getPageTitle(): string {
    return "MOM - Login";
  }

  render(): m.Children {
    return m("form", { onsubmit: (e: Event) => this.submit(e) }, [
      m("input", {
        type: "text",
        placeholder: "Login",
        value: this.login,
        disabled: this.submitting,
        oninput: (e: Event) => (this.login = (e.target as HTMLInputElement).value),
      }),
      m("input", {
        type: "password",
        placeholder: "Password",
        value: this.password,
        disabled: this.submitting,
        oninput: (e: Event) => (this.password = (e.target as HTMLInputElement).value),
      }),
      m("button", { type: "submit", disabled: this.submitting }, "Login"),
      this.error ? m("div", this.error) : null,
    ]);
  }

  private submit(e: Event) {
    e.preventDefault();
    this.submitting = true;
    this.error = undefined;
    ApiEndpoints.Login.call({ login: this.login, password: this.password })
      .then((output) => {
        this.submitting = false;
        if (output.ok && output.sessionId) {
          Auth.setSession(output.sessionId);
          Routing.goToScreen(Endpoints.HOME);
        } else {
          this.error = "Invalid login or password.";
        }
        m.redraw();
      })
      .catch((err: Error) => {
        this.submitting = false;
        this.error = err.message;
        m.redraw();
      });
  }
}
