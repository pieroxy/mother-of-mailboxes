import m from "mithril";

export interface ConnectionTestResult {
  connected: boolean;
  message: string;
}

interface TestConnectionButtonAttrs {
  label: string;
  run: () => Promise<ConnectionTestResult>;
  onResult?: (connected: boolean) => void;
}

/**
 * Runs a connection test and shows its outcome inline. A failed connection is the expected,
 * common outcome of testing, so it's never thrown or toasted.
 */
export class TestConnectionButton implements m.ClassComponent<TestConnectionButtonAttrs> {
  private testing = false;
  private result: ConnectionTestResult | undefined;

  view({ attrs }: m.Vnode<TestConnectionButtonAttrs>): m.Children {
    return m(".test-connection", [
      m("button.secondary", { onclick: () => this.test(attrs), disabled: this.testing },
        this.testing ? "Testing…" : attrs.label),
      this.result
        ? m(".test-connection-result" + (this.result.connected ? ".ok" : ".error"), this.result.message)
        : null,
    ]);
  }

  private test(attrs: TestConnectionButtonAttrs) {
    this.testing = true;
    this.result = undefined;
    attrs.run()
      .then((result) => {
        this.testing = false;
        this.result = result;
        attrs.onResult?.(result.connected);
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
