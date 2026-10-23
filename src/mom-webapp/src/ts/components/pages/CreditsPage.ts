import m from "mithril";
import { AbstractPage } from "./AbstractPage";
import { Auth, AuthStatus } from "../../utils/Auth";
import { Endpoints } from "../../utils/navigation/Endpoints";
import { Routing } from "../../utils/navigation/Routing";

interface Credit {
  name: string;
  url: string;
  use: string;
  license: string;
  licenseUrl: string;
}

const APACHE_2: [string, string] = ["Apache License 2.0", "https://www.apache.org/licenses/LICENSE-2.0"];
const MIT: [string, string] = ["MIT License", "https://opensource.org/licenses/MIT"];

function credit(name: string, url: string, use: string, [license, licenseUrl]: [string, string]): Credit {
  return { name, url, use, license, licenseUrl };
}

const SECTIONS: { title: string; credits: Credit[] }[] = [
  {
    title: "Runtime and server",
    credits: [
      credit("OpenJDK", "https://openjdk.org/", "The Java platform MOM runs on.",
        ["GPL v2 with the Classpath Exception", "https://openjdk.org/legal/gplv2+ce.html"]),
      credit("Apache Tomcat", "https://tomcat.apache.org/", "The embedded web server.", APACHE_2),
    ],
  },
  {
    title: "Mail and authentication",
    credits: [
      credit("JavaMail", "https://javaee.github.io/javamail/", "IMAP and SMTP.",
        ["CDDL or GPL v2 with the Classpath Exception", "https://javaee.github.io/javamail/LICENSE"]),
      credit("Apache James jDKIM", "https://james.apache.org/jdkim/", "DKIM signature verification.", APACHE_2),
      credit("dnsjava", "https://github.com/dnsjava/dnsjava", "DNS lookups for SPF, DKIM, DMARC and delivery checks.",
        ["BSD 3-Clause License", "https://opensource.org/licenses/BSD-3-Clause"]),
      credit("Apache Commons Email", "https://commons.apache.org/proper/commons-email/", "Sending mail.", APACHE_2),
    ],
  },
  {
    title: "Classifiers and text",
    credits: [
      credit("Apache OpenNLP", "https://opennlp.apache.org/", "The spam classifiers.", APACHE_2),
      credit("jsoup", "https://jsoup.org/", "Extracting the text of HTML messages.", MIT),
    ],
  },
  {
    title: "Web UI",
    credits: [
      credit("Mithril", "https://mithril.js.org/", "The web UI framework.", MIT),
      credit("Duotone Basic Interface Icons, by swmansion", "https://www.svgrepo.com/collection/duotone-basic-interface-icons/",
        "The icons, some of them modified.", ["CC BY 4.0", "https://creativecommons.org/licenses/by/4.0/"]),
    ],
  },
  {
    title: "Data",
    credits: [
      credit("Unicode security data (UTS #39)", "https://www.unicode.org/Public/security/latest/",
        "Look-alike characters, for LOOKALIKE_TEXT_EQUALS.", ["Unicode License v3", "https://www.unicode.org/license.txt"]),
    ],
  },
  {
    title: "Libraries",
    credits: [
      credit("Gson", "https://github.com/google/gson", "JSON.", APACHE_2),
      credit("Guava", "https://github.com/google/guava", "Domain names and utilities.", APACHE_2),
      credit("lz4-java", "https://github.com/lz4/lz4-java", "Compressing logs and data files.", APACHE_2),
      credit("Apache Commons Codec", "https://commons.apache.org/proper/commons-codec/", "Encodings.", APACHE_2),
      credit("SLF4J", "https://www.slf4j.org/", "Logging, for the libraries above.", MIT),
    ],
  },
];

/** The software and data MOM is built on. Reachable without logging in, like the footer linking to it. */
export class CreditsPage extends AbstractPage {
  getPageTitle(): string {
    return "MOM - Credits";
  }

  showToolbar(): boolean {
    return Auth.getStatus() === AuthStatus.LOGGED_IN;
  }

  render(): m.Children {
    return m("page.creditspage", [
      m(".page-header", [
        Auth.getStatus() === AuthStatus.NO_SESSION
          ? m("a.page-back", { onclick: () => Routing.goToScreen(Endpoints.LOGIN) }, "‹ Back to login")
          : m("a.page-back", { onclick: () => Routing.goToScreen(Endpoints.HOME) }, "‹ Back home"),
        m("h1.page-title", "Credits"),
      ]),
      m("p.field-hint", "MOM is built on the following software and data. Thanks to everyone behind them."),
      SECTIONS.map((section) => m(".page-card", [
        m("h2", section.title),
        m("ul.credits-list", section.credits.map((c) => m("li", [
          m("a", { href: c.url, target: "_blank", rel: "noopener" }, c.name),
          " — " + c.use + " ",
          m("a.credits-license", { href: c.licenseUrl, target: "_blank", rel: "noopener" }, c.license),
        ]))),
      ])),
    ]);
  }
}
