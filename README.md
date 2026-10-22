# MOM

## What is it?

MOM (Mother Of Mailboxes) is a small daemon that bolts spam detection and routing rules onto any IMAP mailbox — built for people who run their own mail server. On Gmail or Outlook.com? You're already covered, no need for this.

Mail servers and clients often do this natively, but every time you switch software you get to reconfigure it all from scratch. MOM only needs IMAP, so it doesn't care what's behind it: point it at a new server and everything — rules included — just keeps working.

Teaching it a new rule is dead simple too: drop an example email into the right `mom-rules/` subfolder, and every future email matching the same criteria gets routed automatically. See [Learning rules by example](docs/learning.md) for how that folder structure works.

## Requirements

* Java 17.
* An IMAP account, reachable over IMAPS (implicit TLS) — there's no plain-IMAP mode, sorry. Folder-creation rights too, since MOM manages its own `mom-rules/` folder tree; the default on pretty much any account you'd actually own.
* An internet connection — to your mail server, and for the SPF/DKIM/DMARC/FCrDNS checks, which are just DNS lookups. Nothing else ever leaves the box, except when you run [delivery checks](docs/delivery-checks.md).

That's it.

## Features

Brings to your IMAP account:
* SPF, DKIM, DMARC and FCrDNS checks, done by MOM itself
* Rules on the sender (address, domain, regular expression) and the subject, combined with `AND`/`OR`/`NOT`
* Phishing tells: a malformed `From:`, an address hidden in the sender's name, look-alike characters (`Pаypal` with a Cyrillic `а`)
* Spam detection by three classifiers (subject, headers, body), trained locally on your own mail — no dataset, no third-party service
* IP/domain reputation lists, downloaded periodically — never a live per-message lookup, so nothing about a specific email is ever sent anywhere
* Actions: move, mark as read, forward, redirect
* Rules learned by example: drop a message into a folder, and similar mail gets the same treatment

Additionally:

* A web UI to manage accounts and rules, browse per-account stats, and a decision log showing, for each message, every rule it went through and why it matched
* [Delivery checks](docs/delivery-checks.md): is *your* mail set up to be delivered? SPF, DKIM, DMARC and its reports, reverse DNS/HELO, MX, DANE, MTA-STS/TLS-RPT, IP/domain reputation — checked against a real test message when an [SMTP sink](docs/delivery-checks.md#smtp-sink) is configured (a public one is available)
* No third-party service ever sees your mail — filtering only does DNS lookups for SPF/DKIM/DMARC/FCrDNS checks, which reveal sender domains/IPs but never message content.

## Documentation

* New here? [Quick start guide](docs/quickstart.md) — download the jar, try it, then run it as a systemd service.
* [docs/](docs/README.md) — using the web UI, rules, learning by example, spam classifiers, reputation lists, configuration reference.
* [Starter configuration](docs/configuration.md#starter-configuration) — the recommended spam rules, and what each one does.
* [Roadmap](docs/ROADMAP.md) — what's planned next.
