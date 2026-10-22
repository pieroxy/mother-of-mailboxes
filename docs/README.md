# MOM documentation

MOM (Mother Of Mailboxes) is a small daemon that polls one or more IMAP accounts and applies
configurable rules to new mail: move it, mark it read, or both. Rules can be written by hand in
`config.json`, or taught by example by dropping sample messages into special IMAP folders.

- [Quick start](quickstart.md) — download the jar, try it, then run it as a systemd service
- [Running MOM](#running-mom)
- [Configuration file](#configuration-file)
  - [Top-level fields](#top-level-fields)
  - [Account fields](#account-fields)
  - [SMTP settings](#smtp-settings)
  - [Example `config.json`](#example-configjson)
  - [Credentials file](#credentials-file)
- [Matchers and actions](#matchers-and-actions)
- [Rule evaluation order](#rule-evaluation-order)
- [Learning rules by example](#learning-rules-by-example)
  - [Learning shortcuts](#learning-shortcuts)
- [Manually reprocessing a message](#manually-reprocessing-a-message)
- [Logging](#logging)
  - [Access log](#access-log)
  - [Stats log](#stats-log)
  - [Decision log](#decision-log)
- [Data files](#data-files)
- [Classifier corpus collection](#classifier-corpus-collection)
  - [Excluding a folder from the corpus](#excluding-a-folder-from-the-corpus)
  - [Subject classifier training](#subject-classifier-training)
  - [Header classifier training](#header-classifier-training)
  - [Body classifier training](#body-classifier-training)
- [Reputation lists](#reputation-lists)
- [Web server](#web-server)
- [Delivery checks](#delivery-checks)
  - [SMTP sink](#smtp-sink)
- [Third-party data](#third-party-data)

## Running MOM

MOM requires Java 17. Download the jar from the
[latest release](https://github.com/pieroxy/mother-of-mailboxes/releases/latest) (see the
[Quick start](quickstart.md)), then run it with the path to a **directory containing
`config.json` and `credentials.json`**:

```sh
java -jar mom-core-<version>.jar /path/to/config-dir
```

Each account declared in `configurations` is processed on its own:

- New mail in the INBOX is picked up right away (IMAP IDLE). Everything else runs every
  `runEvery` seconds, as does INBOX on a server without IDLE support.
- If a cycle fails, the next one waits longer each time, up to 30 minutes. When the server was
  unreachable, MOM checks every minute whether it's back, and runs a cycle as soon as it is.
- The account's page in the web UI shows its status and last error, and **Run now** starts a
  cycle right away.

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
| `reputationLists` | no | IP/domain reputation lists to download and refresh (see [Reputation lists](#reputation-lists)). Absent means the feature is off. |
| `webServer` | no | Embedded web server serving the web UI (see [Web server](#web-server)). Absent, or `enabled: false`, means the feature is off entirely — no Tomcat startup. |
| `mailDiagnostics` | no | `{"sinkUrl": "https://..."}`: the [SMTP sink](#smtp-sink) used by [delivery checks](#delivery-checks). Absent: no test message, checks are partial. |

### Account fields

Each entry in `configurations` is one IMAP account:

| Field | Required | Description |
|---|---|---|
| `displayName` | yes | Free-form name, used for logging, thread naming, and as the key for this account's state files on disk. Must be filesystem-safe and unique across accounts. |
| `host` | yes | IMAP server hostname. |
| `port` | yes | IMAP server port. |
| `credentials` | yes | Key into `credentials.json`'s `credentials` map, resolved into the IMAP login/password at startup. See [Credentials file](#credentials-file). |
| `runEvery` | yes | Seconds between processing cycles (see [Running MOM](#running-mom) for how IMAP IDLE affects this). |
| `connectTimeout` | no | Seconds to wait for the IMAP or SMTP connection to be established. `0` or absent defaults to 5. |
| `readTimeout` | no | Seconds to wait for the IMAP or SMTP server to answer any single command. `0` or absent defaults to 60. |
| `classifierSpamFolderName` | no | Folder treated as "Spam" for classifier corpus labeling. Defaults to `"Spam"`. |
| `classifierExcludedFolders` | no | Folder names (anywhere in the tree) to skip entirely for classifier corpus collection — neither `SPAM` nor `HAM`, just ignored, like `INBOX`/`mom-rules/` already are. See [Classifier corpus collection](#classifier-corpus-collection). |
| `classifierCorpusRetentionDays` | no | Enables classifier corpus collection for this account when `> 0` (see [Classifier corpus collection](#classifier-corpus-collection)). `0` or absent disables it. |
| `classifierCorpusScanBatchSize` | no | Cap on messages fetched/processed in one corpus scan cycle for this account. `0` or absent defaults to 500. Lower it on a slow link or server; raise it to catch up faster on a fast one. |
| `rules` | no | List of manually-configured rules for this account (see [Matchers and actions](#matchers-and-actions)). Absent/empty means only learned rules (if any) apply. |
| `learningShortcuts` | no | Named flat `mom-rules/<name>` folders bound to a fixed (matcher type, action) pair, as an alternative to the full discovery tree. See [Learning shortcuts](#learning-shortcuts). |
| `discoveryTreeDisabled` | no | Skips creating/maintaining the `<MATCHER_TYPE>/<ACTION_TYPE>` discovery tree under `mom-rules/` entirely. `mom-rules/Done` and any `learningShortcuts` folders are unaffected. Default `false`. Useful with a mail client that shows every IMAP folder unconditionally regardless of subscription state (e.g. Apple Mail), once shortcuts cover what's actually used. See [Learning shortcuts](#learning-shortcuts). |
| `smtp` | no | How this account sends mail (see [SMTP settings](#smtp-settings)). Absent means the account can't send mail. |
| `decisionLogRetentionDays` | no | Days of [decision log](#decision-log) to keep for this account. `0` or absent disables it: it records each message's Subject and From, so it's opt-in. |

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
[Classifier corpus collection](#classifier-corpus-collection) below — sends
very-confidently-classified subjects to `SpamML` pre-marked read, and merely possibly-spammy
ones to `SpamML` left unread for review.

The classifier's verdicts go to `SpamML`, a folder of its own, listed in
`classifierExcludedFolders` — see [Excluding a folder from the corpus](#excluding-a-folder-from-the-corpus).

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
login: until then, the web UI shows only the change-password screen and every other API call is
refused. Changing the password clears the flag.

## Matchers and actions

A rule is a `matcher` + an `action`. When a matcher matches a message, its action runs.

**Matchers**:

| Type | Purpose |
|---|---|
| [`FROM_EQUALS`](matchers/from-equals.md) | Exact match of the full `From:` header. |
| [`FROM_ADDRESS_EQUALS`](matchers/from-address-equals.md) | Match the sender's email address only. |
| [`FROM_DOMAIN_EQUALS`](matchers/from-domain-equals.md) | Match the sender's domain only. |
| [`FROM_ADDRESS_REGEXP`](matchers/from-address-regexp.md) | Match the sender's email address against a regular expression. |
| [`SUBJECT_STARTS_WITH`](matchers/subject-starts-with.md) | Match a prefix of the `Subject:` header. |
| [`SPF_RESULT_EQUALS`](matchers/spf-result-equals.md) | Match a live-verified SPF result. |
| [`DKIM_RESULT_EQUALS`](matchers/dkim-result-equals.md) | Match a live-verified DKIM result. |
| [`DMARC_RESULT_EQUALS`](matchers/dmarc-result-equals.md) | Match a live-evaluated DMARC result (SPF/DKIM domain alignment). |
| [`DMARC_POLICY_EQUALS`](matchers/dmarc-policy-equals.md) | Match the sender domain's published DMARC policy. |
| [`FCRDNS_RESULT_EQUALS`](matchers/fcrdns-result-equals.md) | Match a live-evaluated reverse DNS (FCrDNS) result on the connecting IP. |
| [`FROM_VALIDITY_EQUALS`](matchers/from-validity-equals.md) | Match a missing, duplicated or unparseable `From:` header. |
| [`DISPLAY_NAME_ADDRESS_EQUALS`](matchers/display-name-address-equals.md) | Match an email address in the `From:` display name that differs from the actual one. |
| [`LOOKALIKE_TEXT_EQUALS`](matchers/lookalike-text-equals.md) | Match look-alike characters in the subject or sender name (`Pаypal` with a Cyrillic `а`, `𝐅𝐫𝐞𝐞`). |
| [`SUBJECT_CLASSIFIER_EQUALS`](matchers/subject-classifier-equals.md) | Match when a locally-trained model scores the subject above/below a threshold. |
| [`HEADER_CLASSIFIER_EQUALS`](matchers/header-classifier-equals.md) | Match when a locally-trained model scores a message's headers above/below a threshold. |
| [`BODY_CLASSIFIER_EQUALS`](matchers/body-classifier-equals.md) | Match when a locally-trained model scores a message's body text above/below a threshold. |
| [`IP_REPUTATION_EQUALS`](matchers/ip-reputation-equals.md) | Match when the connecting IP's reputation score (from downloaded lists) crosses a threshold. |
| [`FROM_DOMAIN_REPUTATION_EQUALS`](matchers/from-domain-reputation-equals.md) | Same as above, on the `From:` domain against domain reputation lists. |
| [`AND`](matchers/and.md) | Composite: all children must match. |
| [`OR`](matchers/or.md) | Composite: any child matching is enough. |
| [`NOT`](matchers/not.md) | Composite: matches if its single child does not. |

**Actions**:

| Type | Purpose |
|---|---|
| [`MOVE_TO`](actions/move-to.md) | Move the message to another folder. |
| [`READ`](actions/read.md) | Mark the message as read. |
| [`MOVE_TO_AND_READ`](actions/move-to-and-read.md) | Mark as read, then move. |
| [`AND`](actions/and.md) | Composite: run children in order, stop at first failure. |
| [`OR`](actions/or.md) | Composite: run children in order, stop at first success. |
| [`NOOP`](actions/noop.md) | Does nothing, always succeeds — for observing a match without acting on it, or allow-listing (it still stops evaluation). |
| [`FORWARD`](actions/forward.md) | Forward the message, as an attachment, through the account's SMTP settings. |
| [`REDIRECT`](actions/redirect.md) | Re-send the message unchanged through the account's SMTP settings. |

Every matcher and action config accepts an optional `logLevel` field (`"DEBUG"`, `"INFO"`,
`"WARNING"`, or `"ERROR"`; default `"INFO"`) controlling **only that node's own** log verbosity
— it never affects sibling or child nodes. See [Logging](#logging).

Composite matchers/actions (`AND`/`OR`/`NOT`) take a `children` array of nested matcher/action
configs instead of `key`/`keys`.

A rule's action also accepts two optional, display-only fields, recorded in the decision log
for each message the rule handles:

| Field | Description |
|---|---|
| `name` | Label shown in the decision log. Absent: generated from type and key, e.g. `"→ Backups"`, `"→ Spam (read)"`, `"Mark read"`. |
| `spam` | `true` if the action does spam filtering rather than filing; highlights it in the decision log. Default `false`. |

A rule likewise accepts an optional `name` (alongside `matcher`/`action`), shown in the web UI,
the startup log and the decision log.

## Rule evaluation order

For each new message, MOM walks the rules in `config.json`, in the order they appear, and applies
the **first one whose matcher matches**. A rule "applies" (and stops the search) as soon as its
matcher matches, even if the action itself later fails; a failed action is logged but doesn't
make MOM try the next rule instead.

Learned rules always get a chance to run too, but *where* depends on `config.json`:

* By default (no `LEARNED_RULES` entry anywhere in `rules`), they run **after every
  manually-configured rule**.
* Add an entry with `"type": "LEARNED_RULES"` (no `matcher`, no `action`) anywhere in `rules` to
  run them at that exact position instead — useful when some manual rules (e.g. SPF/DKIM/DMARC
  checks) should always take priority over anything learned, while others (e.g. a catch-all)
  should only apply once the learned rules have had their say:

  ```json
  [
    { "matcher": { "type": "SPF_RESULT_EQUALS", "key": "fail" }, "action": { "type": "MOVE_TO_AND_READ", "key": "Spam" } },
    { "type": "LEARNED_RULES" },
    { "matcher": { "type": "SUBJECT_STARTS_WITH", "key": "[ml-list] " }, "action": { "type": "MOVE_TO", "key": "Lists" } }
  ]
  ```

  At most one `LEARNED_RULES` entry is allowed per account. If a manual rule earlier in the list
  already stopped evaluation (a match without `keepProcessing`), the learned rules — wherever
  they would have run — are skipped entirely, exactly as any rule after that point would be.

Set `"keepProcessing": true` on a rule (alongside `matcher`/`action`, not inside either) to
change that: its action still runs when it matches, but evaluation carries on to the next rule
as if it hadn't, instead of stopping there. Useful when more than one rule should be able to act
on the same message — e.g. a weaker signal marks it read for visibility, while a stronger rule
further down the list still gets a chance to move it:

```json
[
  {
    "matcher": { "type": "SUBJECT_CLASSIFIER_EQUALS", "key": ">0.5" },
    "action": { "type": "READ" },
    "keepProcessing": true
  },
  {
    "matcher": { "type": "SUBJECT_CLASSIFIER_EQUALS", "key": ">0.99" },
    "action": { "type": "MOVE_TO", "key": "Spam" }
  }
]
```

Defaults to `false`.

## Learning rules by example

Instead of writing a rule by hand, you can teach it by dropping example messages into a
specific IMAP folder path:

```
mom-rules/<MATCHER_TYPE>/<ACTION_TYPE>/<key>
```

For example, `mom-rules/FROM_DOMAIN_EQUALS/MOVE_TO/Spam`: every message dropped in that folder
teaches the rule "if the sender's domain equals this message's sender domain, move to Spam".
MOM automatically creates the `<MATCHER_TYPE>/<ACTION_TYPE>` skeleton for every learnable
matcher/action combination each cycle — you only need to create the final `<key>` folder
yourself (its name becomes the action's key, e.g. the target folder for `MOVE_TO`).

Only "learnable" matcher/action types support this (everything except the composite `AND`/`OR`/
`NOT` types, which are reserved for rules you write yourself). Each cycle, for every example message found:

1. The matcher's key is extracted from the example (e.g. its sender's domain).
2. The rule is persisted to `<dataFolder>/<displayName>-learned-rules.json` — a separate file
   from `config.json`, which learning never modifies. If a rule with the same matcher type and
   action already exists, the new key is merged into it (`keys`) rather than creating a
   duplicate rule.
3. The action actually runs on the example message itself.
4. If the example is still present afterward (the action didn't move or delete it), it's moved
   to `mom-rules/Done` — so it isn't re-learned/re-run every cycle. It's marked **unread** on
   the way in, regardless of its state before: a clear indicator in the mail client that
   something there still needs to be manually sorted.

Learned rules come **after** manual rules in evaluation order unless a `LEARNED_RULES` entry
places them elsewhere (see [Rule evaluation order](#rule-evaluation-order)).

### Learning shortcuts

The discovery tree above creates a folder pair for **every** learnable combination, while most
accounts only use a handful. `learningShortcuts` (per account) gives a specific (matcher type,
action) pair its own flat folder directly under `mom-rules/` instead:

```json
{
  "learningShortcuts": [
    {
      "name": "MoveSameDomainToSpam",
      "matcher": { "type": "FROM_DOMAIN_EQUALS" },
      "action": { "type": "MOVE_TO_AND_READ", "key": "Spam" }
    }
  ]
}
```

Dropping a message into `mom-rules/MoveSameDomainToSpam` teaches exactly the same rule as
`mom-rules/FROM_DOMAIN_EQUALS/MOVE_TO_AND_READ/Spam` would. Shortcuts and the discovery tree can
be used side by side.

- The `action` is fully set in the config, `key` included.
- The `matcher` only takes a `type`: its key comes from each example, so a `key` or `keys` there
  is refused.
- A shortcut's name must be unique, and can't be `Done` or a matcher type name.

Most mail clients let you subscribe to just the shortcut folders. Some (notably Apple Mail, on
iOS and macOS) show every folder anyway: for those, set `discoveryTreeDisabled` on the account
so the discovery tree isn't created at all.

## Manually reprocessing a message

To re-run the full rule catalog against a message that's already in your mailbox (for example,
after adding a rule that should have caught it), drop it into `mom-rules/ToProcess`. Every
cycle, MOM treats every message found there exactly as if it had just arrived: the first
matching rule applies normally. If the message is still present afterward — no rule matched, or
the matching rule's action didn't relocate it — it's moved to `mom-rules/Done` (the same folder
used by the learning system above, marked unread the same way) for manual review.

## Logging

Console logging is always on. MOM also always writes to `<dataFolder>/logs/log.txt`, rotated at
midnight into lz4-compressed archives (`log.txt.1.lz4` being the most recent), kept for
`keepLogFiles` days. Browse them from the web UI: the Logs icon in the top bar.

Each matcher/action node in a rule has its own logger, independently leveled via that node's
`logLevel` config field (default `INFO`). Setting `"logLevel": "DEBUG"` on an
[`SPF_RESULT_EQUALS`](matchers/spf-result-equals.md), [`DKIM_RESULT_EQUALS`](matchers/dkim-result-equals.md),
or [`DMARC_RESULT_EQUALS`](matchers/dmarc-result-equals.md) matcher is particularly useful: it
surfaces that rule's full live verification trace (DNS records fetched, mechanisms/signatures
evaluated, alignment computed) without touching any global logging configuration.

### Access log

When the [web server](#web-server) is on, every HTTP request it answers (API, static files, unknown
paths, malformed requests) is appended to `<dataFolder>/logs/access-YYYY-MM-DD.log` (local day),
in the Combined Log Format that Apache and nginx write — so tools like fail2ban or GoAccess read it
as is — followed by the time spent answering:

```
203.0.113.7 - - [16/Oct/2026:11:48:06 +0200] "GET /api/v1/config HTTP/1.1" 404 - "-" "curl/8.4" 3ms
127.0.0.1 - admin [16/Oct/2026:11:48:09 +0200] "GET /api/Session HTTP/1.1" 200 98 "-" "Mozilla/5.0 ..." 1ms
```

- The user field is the web login when the request carried a valid session, `-` otherwise.
- The client address is the one connecting to MOM, unless that's one of the `trustedProxies`:
  then it's read from `X-Forwarded-For`, walked from the right past every trusted proxy, so
  whatever a client puts at the start of that header is never believed.
- The query string is left out for `/api/` paths: it holds the endpoint's JSON input, which can
  carry mail metadata (e.g. a decision log search).

The first request of a day compresses the earlier days into `.lz4`; days older than
`keepLogFiles` are deleted. Browse it from the web UI's Logs page (the Logs icon in the top bar),
next to the application log.

### Stats log

Besides the human-readable log above, MOM appends one JSON line per event to
`<dataFolder>/logs/<displayName>/stats-YYYY-MM-DD.json` (UTC day). Two event types, told apart by `type`:

```json
{"type":"MATCH","matcher":"IpReputationMatcher[spamhaus-drop](score=0.87)","date":"2026-09-18 10:35:12Z"}
{"type":"PROCESSED","result":"MATCH","matcher":"IpReputationMatcher[spamhaus-drop](score=0.87)","processedMs":42,"date":"2026-09-18 10:35:12Z"}
{"type":"PROCESSED","result":"PASS","processedMs":7,"date":"2026-09-18 10:36:01Z"}
```

- **`MATCH`** — one per rule that matched (manual or learned), with the matcher's description
  and detail (a reputation score, a classifier score...). A `keepProcessing` rule followed by
  another matching rule gives two `MATCH` lines.
- **`PROCESSED`** — one per message, once all rules have run. `result` is `MATCH` if a rule
  stopped evaluation (named in `matcher`), `PASS` otherwise. `processedMs` is how long it took.

The web UI's stats page is built from these files. They're never rotated or pruned: delete old
`stats-*.json` files yourself if needed.

### Decision log

Opt-in per account via `decisionLogRetentionDays`: one JSON line per message processed, in
`<dataFolder>/logs/<displayName>/decisions-YYYY-MM-DD.json` (UTC day), explaining where it ended
up and why. Browse it from the web UI: the Logs icon on the account's page, or a matcher on the
stats page.

```json
{"date":"2026-10-13 10:00:00Z","trigger":"INBOX","messageId":"<abc@example.com>","subject":"Hello","from":"Alice <alice@example.com>","processedMs":42,
 "rules":[{"rule":"Rule(...)","learnedRule":false,"matched":false},
          {"rule":"Rule(...)","ruleName":"Spamhaus","learnedRule":false,"matched":true,"matchDetail":"IpReputationMatcher[spamhaus-drop](score=0.87)","actionName":"→ Spam","spam":true,"keepProcessing":false}]}
```

- `trigger`: `INBOX`, `MANUAL_REPROCESSING` (see [Manually reprocessing a message](#manually-reprocessing-a-message)),
  or `LEARNING` (an example dropped in `mom-rules/`: `learnedRule` names the rule learned,
  `reasonForMoveToDone` why it was filed to `mom-rules/Done`, and there's no `rules` array).
- `rules`: every rule reached, in evaluation order; rules after the one that stopped evaluation
  never ran and aren't listed. `matched` is absent if the rule threw before deciding (see
  `exception`). `ruleName`, `actionName` and `spam` are the rule's and action's `name`/`spam` at
  the time (see [Matchers and actions](#matchers-and-actions)); renaming them later doesn't
  rewrite past entries.

Unlike the stats log, it carries mail metadata (Subject, From, Message-ID), so it's rotated:
past days are lz4-compressed, and days older than `decisionLogRetentionDays` deleted.

## Data files

Everything MOM persists lives under `dataFolder`, one file/folder per account (keyed by
`displayName`):

| Path | Contents |
|---|---|
| `logs/log.txt` | Current log file (see [Logging](#logging)); rotated into `logs/log.txt.N.lz4` archives. |
| `logs/access-YYYY-MM-DD.log[.lz4]` | One file per local day of HTTP requests (see [Access log](#access-log)). Compressed after the day ends, deleted after `keepLogFiles` days. |
| `logs/<displayName>/stats-YYYY-MM-DD.json` | One JSON-lines file per UTC day of successful rule matches (see [Stats log](#stats-log)). Not rotated/pruned by MOM. |
| `logs/<displayName>/decisions-YYYY-MM-DD.json[.lz4]` | One JSON-lines file per UTC day of decisions (see [Decision log](#decision-log)), if enabled. Compressed after the day ends, deleted after `decisionLogRetentionDays`. |
| `<displayName>.json` | INBOX UID cursor (which messages have already been processed). |
| `<displayName>-learned-rules.json` | Rules learned via `mom-rules/` (see above). Editable from the account's settings in the web UI, or by hand (then restart MOM). |
| `classifier-corpus/<displayName>-scan-state.json` | Per-folder UID cursor for corpus scanning. |
| `classifier-corpus/<displayName>/classifier-YYYY-MM-DD.json.lz4` | One compressed file per day of collected corpus data. |
| `classifier-corpus/<displayName>/subject-model.bin` | Trained subject classifier model (see below). Absent until enough data has been collected. |
| `classifier-corpus/<displayName>/header-model.bin` | Trained header classifier model (see below). Absent until enough data has been collected. |
| `classifier-corpus/<displayName>/body-model.bin` | Trained body classifier model (see below). Absent until enough data has been collected. |

## Classifier corpus collection

When `classifierCorpusRetentionDays > 0`, MOM builds a labeled dataset per account under
`dataFolder`, walking every folder except `INBOX` and `mom-rules/` and labeling messages `SPAM`
(if in the `classifierSpamFolderName` folder) or `HAM` (everything else).

- The Spam folder is scanned every cycle, so spam you delete by hand is still captured first.
  Every other folder is scanned once a day.
- Only mail received in the last `classifierCorpusRetentionDays` days is collected, and older
  data is pruned once a day.

Filing read mail into folders is what feeds the classifiers examples of legitimate mail: mail
left in `INBOX` teaches them nothing.

### Excluding a folder from the corpus

`classifierExcludedFolders` skips a folder (matched by name, anywhere in the tree) entirely —
neither `SPAM` nor `HAM`.

**If a classifier rule** ([`SUBJECT_CLASSIFIER_EQUALS`](matchers/subject-classifier-equals.md),
[`HEADER_CLASSIFIER_EQUALS`](matchers/header-classifier-equals.md),
[`BODY_CLASSIFIER_EQUALS`](matchers/body-classifier-equals.md)) **moves mail to its own folder,
add that folder here.** Otherwise its content is collected as legitimate mail, and the
classifiers end up training on their own verdicts. Rules moving mail straight to the Spam folder
don't need this.

```json
{ "classifierExcludedFolders": ["SpamML"] }
```

### Subject classifier training

Once a day, after the full scan, MOM retrains a subject classifier on the collected corpus. It
needs **at least 50 examples of each class** (`SPAM` and `HAM`): until then, no model is written
and the matching rules do nothing. The web UI shows each account's progress.

Use [`SUBJECT_CLASSIFIER_EQUALS`](matchers/subject-classifier-equals.md) in a rule to act on it.

### Header classifier training

Same as the subject classifier, but on what the headers tell (sender and recipient domains,
mailing-list headers, `Reply-To`, attachments...). Same minimum: **50 examples of each class**.

Use [`HEADER_CLASSIFIER_EQUALS`](matchers/header-classifier-equals.md) in a rule to act on it.
The classifiers are independent: use any of them, alone or together.

### Body classifier training

Same as the other two, but on the text of the message body, as shown when it's opened. It needs
more data: **at least 150 examples of each class**.

Use [`BODY_CLASSIFIER_EQUALS`](matchers/body-classifier-equals.md) in a rule to act on it.

## Reputation lists

`reputationLists` (top-level, not per-account) declares external IP/domain reputation sources
to download and keep refreshed — never queried live per message, only ever a periodic bulk
download, so no per-message data (sender, IP, subject...) ever leaves the box. Each entry:

```json
{
  "id": "spamhaus-drop",
  "type": "IP_CIDR",
  "url": "https://www.spamhaus.org/drop/drop.txt",
  "refreshHours": 24,
  "score": 1.0
}
```

| Field | Description |
|---|---|
| `id` | Referenced from a matcher's `listIds` (see below). Unique across `reputationLists`. |
| `type` | `IP_CIDR` (one IPv4 or CIDR block per line, e.g. `203.0.113.0/24`) or `DOMAIN` (one domain per line, exact match). Determines which matcher can use the list — `IP_REPUTATION_EQUALS` for `IP_CIDR`, [`FROM_DOMAIN_REPUTATION_EQUALS`](matchers/from-domain-reputation-equals.md) for `DOMAIN`. |
| `url` | `https://...`/`http://...` to download, or `file://...` for a local file you maintain yourself. |
| `refreshHours` | How often to re-download (minimum enforced: 1 hour). |
| `score` | 0 (ok) to 1 (spam): the value attributed to anything found in this list. |

Every list in `reputationLists` is downloaded and kept up to date, whether or not a rule uses it:

- The last downloaded copy is kept on disk and loaded at startup, so lists work from the first
  message, even offline.
- A list is downloaded again once `refreshHours` have passed since the last successful download,
  including across restarts.
- If a download fails (source down, no network), the last copy stays in use. MOM tries again 5
  minutes later, then 10, 20... (never more than `refreshHours` apart) until it succeeds.
- The home page shows each list's age and size. Its **Refresh** button downloads a list right
  away.

In a list file, blank lines are ignored, and `#` or `;` start a comment, on its own line or after
an entry (`1.10.16.0/20 ; SBL256894`). A malformed entry is skipped, not the whole list.

Lists are held in memory. Most are a few MB, but some are much bigger: HaGeZi's NRD7, in the
starter configuration, has ~2.5M entries (~40MB to download). On a small machine, check MOM's
memory use after adding one.

See [Starter configuration](../README.md#starter-configuration) for the free sources wired up by
default.

See [`IP_REPUTATION_EQUALS`](matchers/ip-reputation-equals.md) and
[`FROM_DOMAIN_REPUTATION_EQUALS`](matchers/from-domain-reputation-equals.md) for how a matcher
references one or more lists (`listIds`) and a threshold (`key`, e.g. `">0.5"`) — when several
referenced lists contain the same value, the worst (highest) score wins.

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

**Add account** on the home page opens a wizard that creates an account and, optionally, the
[starter spam rules](../README.md#starter-configuration) — using the account's own spam folder,
a separate classifier folder excluded from the corpus, and only the reputation rules whose lists
are configured in `reputationLists`.

## Delivery checks

Checks that an account's **outgoing** mail is set up to be delivered, rather than rejected or sent to
spam. In the web UI: account page → **Check delivery settings** → **Start diagnostics**. Run on demand,
results aren't kept.

- **Domain checked**: the domain of the account's SMTP From address. No SMTP settings: nothing to check.
- **Test message**: with an [SMTP sink](#smtp-sink) configured, a message is sent through the account's
  SMTP server and read back from the sink, to see how the mail really leaves (sending IP, HELO, envelope
  sender, signatures). Without one, checks requiring it are skipped, those it improves are partial (see
  the table).
- **Statuses**: OK, Warning, Error, Info, Skipped (couldn't run). **Partial** means part of a check
  couldn't run — no test message, or port 25 unreachable from where MOM runs (often blocked on home
  connections).

| Check | Test message | What it checks |
|---|---|---|
| SPF | improves it | One valid record, ≤10 DNS lookups, no `ptr`, ending with `-all`/`~all`. With a test message: the real SPF result for the real sending IP and envelope sender, and whether it aligns with the From domain. |
| DKIM | required | Signed; each signature verifies; key published, ≥2048 bits, not revoked or in testing; no SHA-1, no `l=`; a signature by the From domain. |
| DMARC | improves it | One valid record (own or inherited from the organizational domain), policy strength, `pct`. With a test message: whether it passes, through SPF and/or DKIM. |
| DMARC reports | — | `rua=` present (severity depends on the policy), valid `mailto:` destinations able to receive mail, and authorized when outside your domain. |
| Reverse DNS & HELO | required | The sending IP has a PTR resolving back to it, not a residential-looking one; the HELO name is a real host name, consistent with it. |
| MX | — | Your domain can receive replies and bounces: MX records present, not null, pointing to names (not IPs or aliases) with public addresses. |
| DANE | — | TLSA records on your MX hosts, DNSSEC-signed, matching the certificate each MX presents on port 25. Optional. |
| MTA-STS & TLS-RPT | — | MTA-STS record and policy valid, every MX listed in it and presenting a valid certificate on port 25; TLS-RPT record valid. Optional. |
| IP reputation | improves it | The sending IP, and the rest of its /24, against your `IP_CIDR` [reputation lists](#reputation-lists). Without a test message: the SMTP server's IPs. |
| Domain reputation | improves it | Your From domain — plus envelope and DKIM domains with a test message — against your `DOMAIN` reputation lists. |

What leaves the box during a run:
- DNS lookups for your own domain, MX hosts and sending IP. DANE validates DNSSEC itself; if the
  system resolver doesn't pass DNSSEC data, its lookups go to `1.1.1.1` (shown on the card).
- Connections to your MX hosts on port 25 (DANE, MTA-STS), and an HTTPS fetch of your MTA-STS policy.
- With an SMTP sink: the test message, through your SMTP server.

The test message:
- Goes from the account's From address to `mom-test@<sink domain>`, its subject a random ID.
- One per run, shared by all checks; arrives within seconds usually, given up after 3 minutes.
- Counts toward the account's 50 sends per hour. Your provider may keep a copy in your Sent folder.

### SMTP sink

A receive-only mail server: it accepts the test message, records how it arrived, and hands it back to
MOM. Only that makes the checks see what receivers see.

- **Public instance**: `https://smtp-sink.mom.pieroxy.net` — General settings → Mail Diagnostics →
  **Use the public instance**, then **Test SMTP Sink** and save.
- **Privacy**: whoever runs a sink sees your sending server's IP address and domain, as any recipient of
  your mail would. The message itself only carries a random ID, and is deleted after 5 minutes.
- **Off by default**. Self-hosting a sink is possible, not documented yet.

## Third-party data

- `src/mom-core/src/main/resources/unicode/confusables.txt`, from Unicode's
  [security data](https://www.unicode.org/Public/security/latest/) (UTS #39), © Unicode, Inc.,
  under the [Unicode License v3](https://www.unicode.org/license.txt) (copy alongside it). Used by
  [`LOOKALIKE_TEXT_EQUALS`](matchers/lookalike-text-equals.md).
