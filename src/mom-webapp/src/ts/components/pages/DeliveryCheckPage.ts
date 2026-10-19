import m from "mithril";
import { ApiEndpoints } from "../../auto/ApiEndpoints";
import { CheckStatus, DeliveryCheck, DeliveryCheckResultDto } from "../../auto/pieroxy-mom";
import { AbstractPage } from "./AbstractPage";
import { AccountPageHeader } from "../AccountPageHeader";
import { CheckStatusIcon, checkStatusLabel } from "../CheckStatusIcon";
import { StatusWarningIcon } from "../atoms/icons/StatusWarningIcon";
import { Routing } from "../../utils/navigation/Routing";
import { Endpoints } from "../../utils/navigation/Endpoints";

interface DeliveryCheckPageAttrs {
  accountName: string;
}

/** What the page says about each check before it has run. A new DeliveryCheck needs an entry here. */
const CHECK_INFO: Record<DeliveryCheck, { title: string; shortTitle?: string; description: string }> = {
  [DeliveryCheck.SPF]: {
    title: "SPF",
    description: "The DNS record listing which servers may send mail for your domain (Sender Policy Framework).",
  },
  [DeliveryCheck.DKIM]: {
    title: "DKIM",
    description: "The cryptographic signature proving your mail comes from your domain and wasn't altered on the way "
      + "(DomainKeys Identified Mail). Checked on the test message.",
  },
  [DeliveryCheck.DMARC]: {
    title: "DMARC",
    description: "The DNS record telling receiving servers what to do with mail that fails SPF and DKIM for your domain, "
      + "such as mail forged in your name (Domain-based Message Authentication, Reporting and Conformance).",
  },
  [DeliveryCheck.REVERSE_DNS]: {
    title: "Reverse DNS & HELO",
    shortTitle: "Reverse DNS",
    description: "The name the IP address sending your mail resolves to (PTR record), and the name your server introduces "
      + "itself with (HELO): many receivers distrust a server without a proper, consistent name. Checked on the test message.",
  },
  [DeliveryCheck.MX]: {
    title: "MX",
    description: "The DNS records naming the servers that receive mail for your domain: replies and bounces need them, and "
      + "some receivers distrust mail from a domain that can't be answered.",
  },
  [DeliveryCheck.DANE]: {
    title: "DANE",
    description: "DNSSEC-signed TLSA records pinning your mail servers' certificates, so servers sending you mail can't be "
      + "fooled into talking to an impostor. Optional, but must be right when present: their certificates are checked on port 25.",
  },
  [DeliveryCheck.MTA_STS]: {
    title: "MTA-STS & TLS-RPT",
    shortTitle: "MTA-STS",
    description: "A policy telling servers sending you mail to always encrypt it and check your mail servers' certificates "
      + "(MTA-STS), and where to report when they can't (TLS-RPT). Optional; your mail servers' certificates are checked on port 25.",
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
  /** Undefined until known: no notice rather than a wrong one. */
  private sinkConfigured: boolean | undefined;
  private removed = false;

  getPageTitle(): string {
    return "MOM - Delivery settings - " + this.accountName;
  }

  oninit({ attrs }: m.Vnode<DeliveryCheckPageAttrs>) {
    this.accountName = attrs.accountName;
    ApiEndpoints.AccountStatus.call({ accountName: this.accountName })
      .then((output) => {
        this.sinkConfigured = output.mailDiagnosticsConfigured;
        m.redraw();
      })
      // Not worth an error of its own: the checks still run, and report themselves as partial.
      .catch(() => undefined);
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
      this.sinkConfigured === false ? this.renderNoSinkNotice() : null,
      m(".delivery-check-overview", Object.values(DeliveryCheck).map((check) => this.renderOverviewTile(check))),
      m(".delivery-checks", Object.values(DeliveryCheck).map((check) => this.renderCheck(check))),
    ]);
  }

  private renderNoSinkNotice(): m.Children {
    return m(".delivery-check-notice", [
      m(StatusWarningIcon),
      m("div", [
        m("strong", "Partial diagnostics: no SMTP sink configured."),
        m("p", "Without one, MOM can only read your DNS records: it can't see how your mail actually leaves your server — "
          + "which IP address sends it, for which envelope domain, signed or not. Checks will be partial at best, and some "
          + "will be skipped."),
        m("p", [
          "Set an SMTP sink in ",
          m("a", { onclick: () => Routing.goToScreen(Endpoints.GENERAL_SETTINGS) }, "General settings"),
          ": a public instance is available, or you can host your own.",
        ]),
      ]),
    ]);
  }

  /** Undefined before the check has ever run. */
  private statusOf(check: DeliveryCheck): CheckStatus | undefined {
    return this.running.has(check) ? CheckStatus.IN_PROGRESS : this.results.get(check)?.status;
  }

  /**
   * One tile per check, linking to its card. Not an href="#…": the router owns the URL's
   * fragment (#!/account/…), so the tile scrolls to the card instead.
   */
  private renderOverviewTile(check: DeliveryCheck): m.Children {
    const info = CHECK_INFO[check];
    const status = this.statusOf(check);
    const partial = this.results.get(check)?.partial && !this.running.has(check);
    return m("a.delivery-check-tile" + (status ? ".status-" + status.toLowerCase() : ".not-run"), {
      key: check,
      title: info.title,
      onclick: () => document.getElementById(cardId(check))?.scrollIntoView({ behavior: "smooth", block: "start" }),
    }, [
      status ? m(CheckStatusIcon, { status }) : m(".delivery-check-tile-placeholder"),
      m(".delivery-check-tile-text", [
        m(".delivery-check-tile-name", info.shortTitle ?? info.title),
        m(".delivery-check-tile-status", (status ? checkStatusLabel(status) : "Not run") + (partial ? " · Partial" : "")),
      ]),
    ]);
  }

  private renderCheck(check: DeliveryCheck): m.Children {
    const info = CHECK_INFO[check];
    const result = this.results.get(check);
    const status = this.statusOf(check);
    return m(".page-card.delivery-check", { key: check, id: cardId(check) }, [
      m(".delivery-check-header", [
        status ? m(CheckStatusIcon, { status }) : null,
        m(".delivery-check-title", [
          m("h2", info.title),
          m(".field-hint", info.description),
        ]),
        status ? m(".delivery-check-status.status-" + status.toLowerCase(), [
          checkStatusLabel(status),
          result?.partial && !this.running.has(check)
            ? m("span.delivery-check-partial", { title: "Part of this check couldn't run: see its findings" }, "Partial")
            : null,
        ]) : null,
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
        this.finish(check, { check, status: CheckStatus.ERROR, summary: "The check could not run: " + err.message, partial: false, facts: [], findings: [] });
      })
      .finally(() => m.redraw());
  }

  private finish(check: DeliveryCheck, result: DeliveryCheckResultDto) {
    this.results.set(check, result);
    this.running.delete(check);
    this.progress.delete(check);
  }
}

function cardId(check: DeliveryCheck): string {
  return "delivery-check-" + check.toLowerCase();
}
