import m from "mithril";
import { ApiEndpoints } from "../auto/ApiEndpoints";

interface TestImapConnectionButtonAttrs {
  accountName: string;
  host: string;
  port: number;
  connectTimeout: number;
  readTimeout: number;
  username: string;
  /** Blank falls back to the account's already-saved password server-side — see TestImapConnectionApi. */
  password: string;
  onResult?: (connected: boolean) => void;
}

/**
 * Shared by AccountSettingsPage's read-only IMAP settings card and AccountImapSettingsEditPage's
 * form — both read the exact same staged values (AccountEditSession.workingConfig/workingUsername/
 * workingPassword), so testing from either place tests the same thing. Never treats a failed
 * connection as an error to throw/toast: that's the expected, common outcome of testing, so the
 * result renders inline next to the button instead.
 * AccountCreatePage also uses it, passing onResult to require a successful test.
 */
export class TestImapConnectionButton implements m.ClassComponent<TestImapConnectionButtonAttrs> {
  private testing = false;
  private result: { connected: boolean; message: string } | undefined;

  view({ attrs }: m.Vnode<TestImapConnectionButtonAttrs>): m.Children {
    return m(".test-imap-connection", [
      m("button.secondary", { onclick: () => this.test(attrs), disabled: this.testing },
        this.testing ? "Testing…" : "Test IMAP Connection"),
      this.result
        ? m(".test-imap-connection-result" + (this.result.connected ? ".ok" : ".error"), this.result.message)
        : null,
    ]);
  }

  private test(attrs: TestImapConnectionButtonAttrs) {
    this.testing = true;
    this.result = undefined;
    ApiEndpoints.TestImapConnection.call({
      accountName: attrs.accountName,
      host: attrs.host,
      port: attrs.port,
      connectTimeout: attrs.connectTimeout,
      readTimeout: attrs.readTimeout,
      username: attrs.username,
      password: attrs.password,
    })
      .then((output) => {
        this.testing = false;
        this.result = { connected: output.connected, message: output.message };
        attrs.onResult?.(output.connected);
        m.redraw();
      })
      .catch((err: Error) => {
        this.testing = false;
        this.result = { connected: false, message: err.message };
        attrs.onResult?.(false);
        m.redraw();
      });
  }
}
