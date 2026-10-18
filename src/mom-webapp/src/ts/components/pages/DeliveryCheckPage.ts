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
 * Nothing is kept: leaving the page forgets the results.
 */
export class DeliveryCheckPage extends AbstractPage<DeliveryCheckPageAttrs> {
  private accountName = "";
  private results = new Map<DeliveryCheck, DeliveryCheckResultDto>();
  private running = new Set<DeliveryCheck>();

  getPageTitle(): string {
    return "MOM - Delivery settings - " + this.accountName;
  }

  oninit({ attrs }: m.Vnode<DeliveryCheckPageAttrs>) {
    this.accountName = attrs.accountName;
  }

  render(): m.Children {
    return m("page.deliverycheckpage", [
      m(AccountPageHeader, { accountName: this.accountName, current: "delivery-check" }),
      m(".page-card.delivery-check-intro", [
        m("h2", "Check delivery settings"),
        m("p.field-hint", "Checks that this account's sending domain — the one in its SMTP settings' From address — "
          + "and its mail server are set up so that the mail you send gets delivered, rather than rejected or sent to spam. "
          + "More checks are coming."),
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
        ? m(".delivery-check-summary.pending", "Checking…")
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
    for (const check of Object.values(DeliveryCheck)) this.run(check);
  }

  private run(check: DeliveryCheck) {
    this.running.add(check);
    this.results.delete(check);
    ApiEndpoints.RunDeliveryCheck.call({ accountName: this.accountName, check })
      .then((output) => this.results.set(check, output.result))
      // A failed call (server unreachable, unknown account...) shows on the check itself, like any other problem.
      .catch((err: Error) => this.results.set(check, {
        check, status: CheckStatus.ERROR, summary: "The check could not run: " + err.message, facts: [], findings: [],
      }))
      .finally(() => {
        this.running.delete(check);
        m.redraw();
      });
  }
}
