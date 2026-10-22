# Configuration

[← back to docs](README.md)

The reference for `config.json` and `credentials.json`. Most of it can also be set from the
[web UI](web-ui.md).

## Configuration file

`config.json` (found in the directory passed on the command line) is a plain JSON file.
Credentials (IMAP logins, web server login) live in a separate `credentials.json` file in the
same directory — see [Credentials file](#credentials-file).

Most of it can be edited from the web UI, where changes apply right away. After editing either
file by hand, restart MOM.

### Top-level fields

| Field | Required | Description |
|---|---|---|
| `configurations` | yes | List of accounts to monitor (see below). |
| `dataFolder` | yes | Directory where MOM persists its own state: per-account UID cursors, learned rules, classifier corpus, and logs (`<dataFolder>/logs/log.txt`). Absolute, or relative to the directory holding `config.json` (e.g. `"data"`, so the whole setup can be moved as one folder). Created if missing. |
| `keepLogFiles` | no | Days of application and access logs to keep: both rotate daily into lz4-compressed files. Absent: 30. `0`: never delete. |
| `trustedProxies` | no | Reverse proxies in front of the [web server](#web-server), as IP addresses or CIDR ranges (e.g. `["127.0.0.1", "::1", "172.18.0.0/16"]`). Only from these is the `X-Forwarded-For` header believed, to know the client's real address. Absent or empty: the header is ignored. |
| `reputationLists` | no | IP/domain reputation lists to download and refresh (see [Reputation lists](reputation-lists.md)). Absent means the feature is off. |
| `webServer` | no | Embedded web server serving the web UI (see [Web server](#web-server)). Absent, or `enabled: false`, means the feature is off entirely — no Tomcat startup. |
| `mailDiagnostics` | no | `{"sinkUrl": "https://..."}`: the [SMTP sink](delivery-checks.md#smtp-sink) used by [delivery checks](delivery-checks.md). Absent: no test message, checks are partial. |

### Account fields

Each entry in `configurations` is one IMAP account:

| Field | Required | Description |
|---|---|---|
| `displayName` | yes | Free-form name, used for logging, thread naming, and as the key for this account's state files on disk. Must be filesystem-safe and unique across accounts. |
| `host` | yes | IMAP server hostname. |
| `port` | yes | IMAP server port. |
| `credentials` | yes | Key into `credentials.json`'s `credentials` map, resolved into the IMAP login/password at startup. See [Credentials file](#credentials-file). |
| `runEvery` | yes | Seconds between processing cycles. New mail in the INBOX is picked up right away anyway (IMAP IDLE), unless the server doesn't support it. |
| `connectTimeout` | no | Seconds to wait for the IMAP or SMTP connection to be established. `0` or absent defaults to 5. |
| `readTimeout` | no | Seconds to wait for the IMAP or SMTP server to answer any single command. `0` or absent defaults to 60. |
| `classifierSpamFolderName` | no | Folder treated as "Spam" for classifier corpus labeling. Defaults to `"Spam"`. |
| `classifierExcludedFolders` | no | Folder names (anywhere in the tree) to skip entirely for classifier corpus collection — neither `SPAM` nor `HAM`, just ignored, like `INBOX`/`mom-rules/` already are. See [Classifier corpus collection](classifiers.md#classifier-corpus-collection). |
| `classifierCorpusRetentionDays` | no | Enables classifier corpus collection for this account when `> 0` (see [Classifier corpus collection](classifiers.md#classifier-corpus-collection)). `0` or absent disables it. |
| `classifierCorpusScanBatchSize` | no | Cap on messages fetched/processed in one corpus scan cycle for this account. `0` or absent defaults to 500. Lower it on a slow link or server; raise it to catch up faster on a fast one. |
| `rules` | no | List of manually-configured rules for this account (see [Matchers and actions](rules.md#matchers-and-actions)). Absent/empty means only learned rules (if any) apply. |
| `learningShortcuts` | no | Named flat `mom-rules/<name>` folders bound to a fixed (matcher type, action) pair, as an alternative to the full discovery tree. See [Learning shortcuts](learning.md#learning-shortcuts). |
| `discoveryTreeDisabled` | no | Skips creating/maintaining the `<MATCHER_TYPE>/<ACTION_TYPE>` discovery tree under `mom-rules/` entirely. `mom-rules/Done` and any `learningShortcuts` folders are unaffected. Default `false`. Useful with a mail client that shows every IMAP folder unconditionally regardless of subscription state (e.g. Apple Mail), once shortcuts cover what's actually used. See [Learning shortcuts](learning.md#learning-shortcuts). |
| `smtp` | no | How this account sends mail (see [SMTP settings](#smtp-settings)). Absent means the account can't send mail. |
| `decisionLogRetentionDays` | no | Days of [decision log](logs.md#decision-log) to keep for this account. `0` or absent disables it: it records each message's Subject and From, so it's opt-in. |

Connections are always made over IMAPS (implicit TLS) — there is no plain-IMAP option.

### SMTP settings

```json
"smtp": {
  "host": "smtp.example.com",
  "port": 587,
  "security": "STARTTLS",
  "fromAddress": "me@example.com"
}
```

| Field | Required | Description |
|---|---|---|
| `host` | yes | SMTP server hostname. |
| `port` | yes | SMTP server port. |
| `security` | yes | `TLS` (encrypted from the start, usually port 465) or `STARTTLS` (upgraded before logging in, usually port 587; refused if the server doesn't offer it). There is no plain-SMTP option. |
| `fromAddress` | yes | Sender address of the mail MOM sends for this account. |
| `credentials` | no | Key into `credentials.json`'s `credentials` map for the SMTP login. Absent: the account's IMAP login is used. |

The server's certificate is checked against `host`, and the account's `connectTimeout`/`readTimeout` apply.

### Example `config.json`

```json
{
  "dataFolder": "/var/lib/mom",
  "keepLogFiles": 14,
  "configurations": [
    {
      "displayName": "personal",
      "host": "imap.example.com",
      "port": 993,
      "credentials": "personal",
      "runEvery": 600,
      "classifierSpamFolderName": "Spam",
      "classifierExcludedFolders": ["SpamML"],
      "classifierCorpusRetentionDays": 90,
      "rules": [
        {
          "matcher": { "type": "SPF_RESULT_EQUALS", "key": "fail", "logLevel": "DEBUG" },
          "action": { "type": "MOVE_TO_AND_READ", "key": "Spam" }
        },
        {
          "matcher": { "type": "SPF_RESULT_EQUALS", "key": "softfail" },
          "action": { "type": "MOVE_TO", "key": "Spam" }
        },
        {
          "matcher": {
            "type": "AND",
            "children": [
              { "type": "DMARC_RESULT_EQUALS", "key": "fail" },
              { "type": "DMARC_POLICY_EQUALS", "key": "reject" }
            ]
          },
          "action": { "type": "MOVE_TO_AND_READ", "key": "Spam" }
        },
        {
          "matcher": { "type": "FCRDNS_RESULT_EQUALS", "key": "none" },
          "action": { "type": "MOVE_TO", "key": "Spam" }
        },
        {
          "matcher": {
            "type": "AND",
            "children": [
              { "type": "FROM_DOMAIN_EQUALS", "key": "newsletter.example.com" },
              { "type": "DKIM_RESULT_EQUALS", "key": "pass" }
            ]
          },
          "action": { "type": "MOVE_TO", "key": "Newsletters" }
        },
        {
          "matcher": { "type": "FROM_ADDRESS_EQUALS", "keys": ["boss@example.com", "hr@example.com"] },
          "action": { "type": "READ" }
        },
        {
          "matcher": { "type": "SUBJECT_CLASSIFIER_EQUALS", "key": ">0.99" },
          "action": { "type": "MOVE_TO_AND_READ", "key": "SpamML" }
        },
        {
          "matcher": { "type": "SUBJECT_CLASSIFIER_EQUALS", "key": ">0.5" },
          "action": { "type": "MOVE_TO", "key": "SpamML" }
        }
      ]
    }
  ]
}
```

This example: sends SPF hard-failures to Spam pre-marked read; sends DMARC failures from
domains that publish a `reject` policy (see [`DMARC_POLICY_EQUALS`](matchers/dmarc-policy-equals.md))
to Spam pre-marked read too; sends SPF softfails to Spam unread (for manual review); routes a
newsletter domain to a `Newsletters` folder only when it also carries a valid DKIM signature for
that domain; marks mail from two trusted addresses as read (using `keys` to match either one)
without moving it; and — once enough data has been collected, see
[Classifier corpus collection](classifiers.md#classifier-corpus-collection) below — sends
very-confidently-classified subjects to `SpamML` pre-marked read, and merely possibly-spammy
ones to `SpamML` left unread for review.

The classifier's verdicts go to `SpamML`, a folder of its own, listed in
`classifierExcludedFolders` — see [Excluding a folder from the corpus](classifiers.md#excluding-a-folder-from-the-corpus).

The `FCRDNS_RESULT_EQUALS: none` rule is here to show the syntax: on its own it's a weak signal
(plenty of legitimate small mail servers have no reverse DNS), better combined with another one
via `AND`.

### Credentials file

`credentials.json`, in the same directory as `config.json`, holds every secret (IMAP logins,
web server login) instead of `config.json` itself — so `config.json` can be shared, backed up,
or committed without leaking anything. Each account's `credentials` field is a key into this
file's `credentials` map:

```json
{
  "credentials": {
    "personal": {
      "username": "me@example.com",
      "password": "secret"
    }
  }
}
```

Every `credentials` key referenced from `config.json` must exist in this map — MOM fails fast
at startup otherwise.

On the web server's credential only, `"temporary": true` forces a password change right after
login. See [First start](running.md#first-start) to reset a lost web UI password.

## Web server

MOM can embed a web server (Tomcat) serving a web UI, controlled by the top-level `webServer`
block:

```json
"webServer": {
  "enabled": true,
  "httpPort": 8080,
  "address": "127.0.0.1",
  "credentials": "webui"
}
```

| Field | Required | Description |
|---|---|---|
| `enabled` | no | Turns the web server on. Absent, or `false`, means it's fully off: no Tomcat startup at all, no port bound. |
| `httpPort` | yes if `enabled` | Port the web server listens on. |
| `address` | no | Address to bind to. Absent binds all interfaces. |
| `credentials` | yes if `enabled` | Key into `credentials.json`'s `credentials` map (see [Credentials file](#credentials-file)). |
| `secureCookie` | no | Marks the session cookie `Secure`, so browsers only send it over HTTPS. Set it when the UI is served over HTTPS (e.g. behind a TLS reverse proxy): with it, logging in over plain HTTP no longer works. Default `false`. |

Failed logins are counted per client address (the real one behind a [trusted proxy](#top-level-fields)):
after 5 failures within 5 minutes, that address is refused (HTTP 429, with `Retry-After`) until the
oldest failure is 5 minutes old, even with the right password. A successful login clears its count,
and other addresses are never affected. Each failed or refused login is logged at `WARNING` with the
address.


## Starter configuration

These are the rules the web UI's account wizard creates when you choose the recommended spam
rules (only the reputation rules whose lists are configured).

[`config.example.json`](../config.example.json) holds the same configuration, to start from by
hand: copy it as `config.json` and fill in `host`, then copy
[`credentials.example.json`](../credentials.example.json) as `credentials.json` and fill in
`username`/`password`.

Strong signals send mail to Spam marked read; weaker ones leave it unread, so unread mail in Spam
is what's worth a look. What it does:

* **SPF/DKIM `fail`, and DMARC `fail` from a domain with a `reject` policy** → Spam, pre-marked
  read — the strong, protocol-verified signals. DMARC `fail` with a `quarantine` or `none` policy
  → Spam, left unread.
* **Missing, duplicated or unparseable `From:`**
  ([`FROM_VALIDITY_EQUALS`](matchers/from-validity-equals.md)) → Spam, left unread: RFC 5322
  violations that DMARC can't evaluate, but which home-made senders (scripts, NAS) sometimes produce.
* **Look-alike characters in the subject or sender name**
  ([`LOOKALIKE_TEXT_EQUALS`](matchers/lookalike-text-equals.md)): `Pаypal` with a Cyrillic
  `а`, an all-Cyrillic `Рау` among English words, `𝐅𝐫𝐞𝐞` → Spam, left unread.
* **SPF `softfail` + FCrDNS `fail`/`none`** (`AND`) → Spam, left unread for review.
  [`FCRDNS_RESULT_EQUALS`](matchers/fcrdns-result-equals.md) is the weakest of the four, so
  it's never used standalone here, only as corroboration.
* **Subject classifier** (`SUBJECT_CLASSIFIER_EQUALS >0.99`) → `SpamML` (its own folder, not
  `Spam`), pre-marked read. Does nothing until trained on **at least 50 examples of each class**
  — see [Classifier corpus collection](classifiers.md#classifier-corpus-collection).
  `classifierExcludedFolders` keeps `SpamML` out of corpus collection — otherwise it'd get
  scanned and mislabeled `HAM`, and the classifier would train on its own past verdicts. See
  [Excluding a folder from the corpus](classifiers.md#excluding-a-folder-from-the-corpus).
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
  everything else here — see [Reputation lists](reputation-lists.md).

See [Reputation lists](reputation-lists.md) for how these are downloaded (once for
the whole process, refreshed periodically, never queried live per message).
