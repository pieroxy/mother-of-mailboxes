import m from "mithril";
import { ApiEndpoints } from "../auto/ApiEndpoints";

export function LoginForm(): m.Component {
  let login = "";
  let password = "";
  let submitting = false;
  let loggedIn = false;
  let error: string | undefined;

  function submit(e: Event) {
    e.preventDefault();
    submitting = true;
    error = undefined;
    ApiEndpoints.Login.call({ login, password })
      .then((output) => {
        submitting = false;
        loggedIn = output.ok;
        if (!output.ok) error = "Login ou mot de passe incorrect.";
        m.redraw();
      })
      .catch((e: Error) => {
        submitting = false;
        error = e.message;
        m.redraw();
      });
  }

  return {
    view: () =>
      m("form", { onsubmit: submit }, [
        m("input", {
          type: "text",
          placeholder: "Login",
          value: login,
          disabled: submitting,
          oninput: (e: Event) => (login = (e.target as HTMLInputElement).value),
        }),
        m("input", {
          type: "password",
          placeholder: "Password",
          value: password,
          disabled: submitting,
          oninput: (e: Event) => (password = (e.target as HTMLInputElement).value),
        }),
        m("button", { type: "submit", disabled: submitting }, "Login"),
        error ? m("div", error) : null,
        loggedIn ? m("div", "Connecté.") : null,
      ]),
  };
}
