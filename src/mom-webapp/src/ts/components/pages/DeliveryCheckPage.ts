import m from "mithril";
import { ApiEndpoints } from "../../auto/ApiEndpoints";
import { CheckStatus, DeliveryCheck, DeliveryCheckResultDto } from "../../auto/pieroxy-mom";
import { AbstractPage } from "./AbstractPage";
import { AccountPageHeader } from "../AccountPageHeader";
import { CheckStatusIcon, checkStatusLabel } from "../CheckStatusIcon";

interface DeliveryCheckPageAttrs {
  accountName: string;
}

/** What the page says about each check before it has run. A new DeliveryCheck needs an entry here. */
const CHECK_INFO: Record<DeliveryCheck, { title: string; description: string }> = {
  [DeliveryCheck.SPF]: {
    title: "SPF",
    description: "The DNS record listing which servers may send mail for your domain (Sender Policy Framework).",
  },
};

/**
 * Checks an account's delivery settings on demand: "Start diagnostics" runs every DeliveryCheck,
 * each through its own RunDeliveryCheckApi call so results show up one by one as they come in.
 * A check waiting on the run's test send comes back IN_PROGRESS, and is called again — with the
 * same run id, so the whole run shares one test send — until it's done. Nothing is kept: leaving
 * the page forgets the results.
 */
export class DeliveryCheckPage extends AbstractPage<DeliveryCheckPageAttrs> {
  private accountName = "";
  private results = new Map<DeliveryCheck, DeliveryCheckResultDto>();
  private running = new Set<DeliveryCheck>();
  /** Progress reported by a check still running, e.g. "Waiting for the test message (12s so far)…". */
  private progress = new Map<DeliveryCheck, string>();
  private runId = "";
  private removed = false;

  getPageTitle(): string {
    return "MOM - Delivery settings - " + this.accountName;
  }

  oninit({ attrs }: m.Vnode<DeliveryCheckPageAttrs>) {
    this.accountName = attrs.accountName;
  }

  onremove() {
    this.removed = true;
  }

  render(): m.Children {
    return m("page.deliverycheckpage", [
      m(AccountPageHeader, { accountName: this.accountName, current: "delivery-check" }),
      m(".page-card.delivery-check-intro", [
        m("h2", "Check delivery settings"),
        m("p.field-hint", "Checks that this account's sending domain — the one in its SMTP settings' From address — "
          + "and its mail server are set up so that the mail you send gets delivered, rather than rejected or sent to spam. "
          + "More checks are coming."),
        m("p.field-hint", "If an SMTP sink is set in General settings, a test message is sent through this account's SMTP "
          + "server to see how your mail really leaves: it can take a minute or two, and your provider may keep a copy of it "
          + "in your Sent folder."),
        m("button", { onclick: () => this.runAll(), disabled: this.running.size > 0 },
          this.running.size > 0 ? "Running…" : "Start diagnostics"),
      ]),
      m(".delivery-checks", Object.values(DeliveryCheck).map((check) => this.renderCheck(check))),
    ]);
  }

  private renderCheck(check: DeliveryCheck): m.Children {
    const info = CHECK_INFO[check];
    const result = this.results.get(check);
    const status = this.running.has(check) ? CheckStatus.IN_PROGRESS : result?.status;
    return m(".page-card.delivery-check", { key: check }, [
      m(".delivery-check-header", [
        status ? m(CheckStatusIcon, { status }) : null,
        m(".delivery-check-title", [
          m("h2", info.title),
          m(".field-hint", info.description),
        ]),
        status ? m(".delivery-check-status.status-" + status.toLowerCase(), checkStatusLabel(status)) : null,
      ]),
      this.running.has(check)
        ? m(".delivery-check-summary.pending", this.progress.get(check) ?? "Checking…")
        : result ? this.renderResult(result) : m(".delivery-check-summary.pending", "Not run yet."),
    ]);
  }

  private renderResult(result: DeliveryCheckResultDto): m.Children {
    return [
      m(".delivery-check-summary", result.summary),
      result.facts.length > 0
        ? m("dl.delivery-check-facts", result.facts.map((fact) => [m("dt", fact.label), m("dd", fact.value)]))
        : null,
      result.findings.length > 0
        ? m("ul.delivery-check-findings", result.findings.map((finding) =>
          m("li", [m(CheckStatusIcon, { status: finding.status }), m("span", finding.message)])))
        : null,
    ];
  }

  private runAll() {
    // Not crypto.randomUUID(): it only exists on HTTPS pages, and MOM may well be served over plain HTTP.
    this.runId = Date.now().toString(36) + "-" + Math.random().toString(36).slice(2);
    for (const check of Object.values(DeliveryCheck)) {
      this.running.add(check);
      this.results.delete(check);
      this.progress.delete(check);
      this.run(check, this.runId);
    }
  }

  private run(check: DeliveryCheck, runId: string) {
    ApiEndpoints.RunDeliveryCheck.call({ accountName: this.accountName, check, runId })
      .then((output) => {
        if (this.removed || runId !== this.runId) return;
        if (output.result.status === CheckStatus.IN_PROGRESS) {
          // The server already waited a while before answering: call again right away.
          this.progress.set(check, output.result.summary);
          this.run(check, runId);
        } else {
          this.finish(check, output.result);
        }
      })
      // A failed call (server unreachable, unknown account...) shows on the check itself, like any other problem.
      .catch((err: Error) => {
        if (runId !== this.runId) return;
        this.finish(check, { check, status: CheckStatus.ERROR, summary: "The check could not run: " + err.message, facts: [], findings: [] });
      })
      .finally(() => m.redraw());
  }

  private finish(check: DeliveryCheck, result: DeliveryCheckResultDto) {
    this.results.set(check, result);
    this.running.delete(check);
    this.progress.delete(check);
  }
}
