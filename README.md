# MOM

## What is it?

MOM (Mother Of Mailboxes) is a small daemon that bolts spam detection and routing rules onto any IMAP mailbox — built for people who run their own mail server. On Gmail or Outlook.com? You're already covered, no need for this.

Mail servers and clients often do this natively, but every time you switch software you get to reconfigure it all from scratch. MOM only needs IMAP, so it doesn't care what's behind it: point it at a new server and everything — rules included — just keeps working.

Teaching it a new rule is dead simple too: drop an example email into the right `mom-rules/` subfolder, and every future email matching the same criteria gets routed automatically. See [Learning rules by example](docs/README.md#learning-rules-by-example) for how that folder structure works.

## Requirements

* Java 17.
* An IMAP account, reachable over IMAPS (implicit TLS) — there's no plain-IMAP mode, sorry. Folder-creation rights too, since MOM manages its own `mom-rules/` folder tree; the default on pretty much any account you'd actually own.
* An internet connection — to your mail server, and for the SPF/DKIM/DMARC/FCrDNS checks, which are just DNS lookups. Nothing else ever leaves the box.

That's it.

## Features

Brings to your IMAP account:
* SPF, DKIM, DMARC and FCrDNS checks
* Rules based on **from** and **subject**
* ML-based spam detection ([`SUBJECT_CLASSIFIER_EQUALS`](docs/matchers/subject-classifier-equals.md)), trained locally on your own emails — no dataset, no third-party service, just a model built and kept from your own mailbox as it grows
* IP/domain reputation scoring ([`IP_REPUTATION_EQUALS`](docs/matchers/ip-reputation-equals.md)/[`FROM_DOMAIN_REPUTATION_EQUALS`](docs/matchers/from-domain-reputation-equals.md)) against lists you download yourself — periodic bulk download, never a live per-message lookup, so nothing about a specific email is ever sent anywhere

Additionally:

* Logs show exactly which rule acted on each message
* No third-party service ever sees your mail — the only outbound traffic is DNS lookups for SPF/DKIM/DMARC/FCrDNS checks, which reveal sender domains/IPs but never message content.

## Concepts

A few things worth understanding before you dive in:

* **No UI, no manual rule editing** — the primary way to teach MOM a rule is to drop an example email into the right `mom-rules/` subfolder. See [Learning rules by example](docs/README.md#learning-rules-by-example).
* **First match wins** — rules are evaluated in the order they appear in `config.json` (learned rules included, wherever you place them — after everything else by default); the first one that matches runs its action and evaluation stops there. See [Rule evaluation order](docs/README.md#rule-evaluation-order).
* **Everything is HAM except Spam** — for [classifier corpus collection](docs/README.md#classifier-corpus-collection), every folder is treated as legitimate mail (HAM) except the configured Spam folder. `INBOX`, `mom-rules/`, and any [excluded folders](docs/README.md#excluding-a-folder-from-the-corpus) (e.g. `SpamML`) are skipped entirely rather than counted as either.
* **INBOX doesn't count** — INBOX is never scanned for the corpus, so mail you leave sitting there teaches the classifier nothing. Filing/archiving read mail into folders (an "inbox zero" habit) is what actually feeds it examples of legitimate mail.
* **Unread in Spam means "review me"** — by convention (see the [starter config](config.example.json)), strong verdicts (SPF/DKIM/DMARC `fail`) are moved to Spam pre-marked read, while weaker, corroborating-only signals are left unread — a manual-review flag, since MOM has no UI to show confidence.
* **Always verified live** — SPF/DKIM/DMARC/FCrDNS are recomputed from scratch via DNS on every check; any `Authentication-Results`/`Received-SPF` header already on the message is never trusted, since anyone could have forged it before delivery.
* **Reputation lists are the opposite: never live** — [`IP_REPUTATION_EQUALS`](docs/matchers/ip-reputation-equals.md)/[`FROM_DOMAIN_REPUTATION_EQUALS`](docs/matchers/from-domain-reputation-equals.md) check IPs/domains against lists downloaded in bulk ahead of time, for the whole process — never a query per message. Each source is called again once `refreshHours` has elapsed. See [Reputation lists](docs/README.md#reputation-lists).
* **Manual reprocessing** — drop any message into `mom-rules/ToProcess` to run the current rule set against it (handy for reclassifying an old message after adding or fixing a rule); it ends up in `mom-rules/Done` once handled, whether or not a rule actually matched. See [Manually reprocessing a message](docs/README.md#manually-reprocessing-a-message).

## Roadmap

* Feed the classifier more than just the subject — sender, recipient, IP.
* Simple web UI to edit more complex rules.
* More matchers:
    * Recipient-based: TO_EQUALS/CC_EQUALS/recipient-domain matching
    * Subject: CONTAINS, MATCHES (regex)
    * Generic headers: HEADER_EQUALS/HEADER_CONTAINS(name, value)
    * Body: BODY_CONTAINS, BODY_MATCHES
    * SIZE
    * Attachment: HAS_ATTACHMENT, FILENAME_ENDS_WITH, FILENAME_IS
    * Dated: MESSAGE_AGE, MESSAGE_DATE
    * IMAP_FLAGS
* More actions:
    * COPY_TO
    * DELETE
    * SET_FLAG
    * FORWARD/REDIRECT/REPLY — Later, it's a much bigger endeavor

## Documentation

* New here? [Quick start guide](docs/quickstart.md) — download the jar, try it, then run it as a systemd service.
* [docs/](docs/README.md) — how MOM works, the configuration reference, a dedicated page for every matcher and action.
* [`docs/IMPLEMENTATION-DETAILS.md`](docs/IMPLEMENTATION-DETAILS.md) — digging into the code? The "why" behind a few non-obvious internals.

## Starter configuration

[`config.example.json`](config.example.json) is a reasonable config to start from: SPF, DKIM,
DMARC, FCrDNS, and reputation lists all enabled, with sane logging. Copy it to your `dataFolder`
as `config.json` and fill in `host`; copy
[`credentials.example.json`](credentials.example.json) as `credentials.json` and fill in
`username`/`password`. What it does:

* **SPF/DKIM/DMARC `fail`** → Spam, pre-marked read — the strong, protocol-verified signals.
* **SPF `softfail` + FCrDNS `fail`/`none`** (`AND`) → Spam, left unread for review.
  [`FCRDNS_RESULT_EQUALS`](docs/matchers/fcrdns-result-equals.md) is the weakest of the four, so
  it's never used standalone here, only as corroboration.
* **Subject classifier** (`SUBJECT_CLASSIFIER_EQUALS >0.99`) → `SpamML` (its own folder, not
  `Spam`), pre-marked read. Does nothing until trained on **at least 50 examples of each class**
  — see [Classifier corpus collection](docs/README.md#classifier-corpus-collection).
  `classifierExcludedFolders` keeps `SpamML` out of corpus collection — otherwise it'd get
  scanned and mislabeled `HAM`, and the classifier would train on its own past verdicts. See
  [Excluding a folder from the corpus](docs/README.md#excluding-a-folder-from-the-corpus).
* **IP reputation** — [Spamhaus DROP](https://www.spamhaus.org/drop/drop.txt) and
  [FireHOL's `blocklist_de_mail`](https://raw.githubusercontent.com/firehol/blocklist-ipsets/master/blocklist_de_mail.ipset),
  two independent feeds (different methodologies, so agreement means more than either alone):
  both match → Spam pre-marked read; only one → Spam left unread.
* **Domain reputation** — same `AND`/`OR` pattern, with
  [HaGeZi's TIF mini](https://github.com/hagezi/dns-blocklists) and the
  [Blocklist Project](https://github.com/blocklistproject/Lists)'s phishing list.
* **Disposable email domains** — a
  [community list](https://github.com/disposable-email-domains/disposable-email-domains) of
  throwaway providers, used standalone → Spam left unread. A different *kind* of signal:
  anonymous, not necessarily malicious.
* **Newly registered domains** — [HaGeZi's NRD7](https://github.com/hagezi/nrd), domains
  registered in the last 7 days, standalone → Spam left unread. Same reasoning as disposable
  domains: unusual, not proof. Heads up: this one's ~2.5M entries/~40MB, much bigger than
  everything else here — see [Reputation lists](docs/README.md#reputation-lists).

See [Reputation lists](docs/README.md#reputation-lists) for how these are downloaded (once for
the whole process, refreshed periodically, never queried live per message).

