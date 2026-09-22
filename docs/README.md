# MOM documentation

MOM (Mother Of Mailboxes) is a small daemon that polls one or more IMAP accounts and applies
configurable rules to new mail: move it, mark it read, or both. Rules can be written by hand in
`config.json`, or taught by example by dropping sample messages into special IMAP folders.

- [Quick start](quickstart.md) — download the jar, try it, then run it as a systemd service
- [Running MOM](#running-mom)
- [Configuration file](#configuration-file)
  - [Top-level fields](#top-level-fields)
  - [Account fields](#account-fields)
  - [Example `config.json`](#example-configjson)
  - [Credentials file](#credentials-file)
- [Matchers and actions](#matchers-and-actions)
- [Rule evaluation order](#rule-evaluation-order)
- [Learning rules by example](#learning-rules-by-example)
  - [Learning shortcuts](#learning-shortcuts)
- [Manually reprocessing a message](#manually-reprocessing-a-message)
- [Logging](#logging)
  - [Stats log](#stats-log)
- [Data files](#data-files)
- [Classifier corpus collection](#classifier-corpus-collection)
  - [Excluding a folder from the corpus](#excluding-a-folder-from-the-corpus)
  - [Subject classifier training](#subject-classifier-training)
  - [Header classifier training](#header-classifier-training)
  - [Body classifier training](#body-classifier-training)
- [Reputation lists](#reputation-lists)
- [Web server](#web-server)

## Running MOM

MOM requires Java 17. Build a runnable jar with Maven, then run it with the path to a
**directory containing `config.json` and `credentials.json`**:

```sh
mvn clean package
java -jar target/mom-core-1.0-SNAPSHOT.jar /path/to/config-dir
```

One thread is started per account declared in `configurations`. Each account is processed on
its own schedule (`runEvery`, see below), backing off automatically (up to 30 minutes) if a
cycle keeps failing (e.g. the IMAP server is unreachable).

Between cycles, the account watches its INBOX via IMAP IDLE (RFC 2177), so new mail triggers the
next cycle immediately instead of waiting out `runEvery`. Falls back to plain polling if the
server doesn't support IDLE.

## Configuration file

`config.json` (found in the directory passed on the command line) is a plain JSON file, parsed
field-for-field into Java objects — the JSON keys match the Java field names exactly (camelCase).
Credentials (IMAP logins, web server login) live in a separate `credentials.json` file in the
same directory — see [Credentials file](#credentials-file).

### Top-level fields

| Field | Required | Description |
|---|---|---|
| `configurations` | yes | List of accounts to monitor (see below). |
| `dataFolder` | yes | Directory where MOM persists its own state: per-account UID cursors, learned rules, classifier corpus, and logs (`<dataFolder>/logs/log.txt`). Created if missing. |
| `keepLogFiles` | no | Number of rotated, lz4-compressed daily log files to keep. `0` or absent disables rotation (the log file just keeps growing). |
| `reputationLists` | no | IP/domain reputation lists to download and refresh (see [Reputation lists](#reputation-lists)). Absent means the feature is off. |
| `webServer` | no | Embedded web server serving the web UI (see [Web server](#web-server)). Absent, or `enabled: false`, means the feature is off entirely — no Tomcat startup. |

### Account fields

Each entry in `configurations` is one IMAP account:

| Field | Required | Description |
|---|---|---|
| `displayName` | yes | Free-form name, used for logging, thread naming, and as the key for this account's state files on disk. Must be filesystem-safe and unique across accounts. |
| `host` | yes | IMAP server hostname. |
| `port` | yes | IMAP server port. |
| `credentials` | yes | Key into `credentials.json`'s `credentials` map, resolved into the IMAP login/password at startup. See [Credentials file](#credentials-file). |
| `runEvery` | yes | Seconds between processing cycles (see [Running MOM](#running-mom) for how IMAP IDLE affects this). |
| `classifierSpamFolderName` | no | Folder treated as "Spam" for classifier corpus labeling. Defaults to `"Spam"`. |
| `classifierExcludedFolders` | no | Folder names (anywhere in the tree) to skip entirely for classifier corpus collection — neither `SPAM` nor `HAM`, just ignored, like `INBOX`/`mom-rules/` already are. See [Classifier corpus collection](#classifier-corpus-collection). |
| `classifierCorpusRetentionDays` | no | Enables classifier corpus collection for this account when `> 0` (see [Classifier corpus collection](#classifier-corpus-collection)). `0` or absent disables it. |
| `classifierCorpusScanBatchSize` | no | Cap on messages fetched/processed in one corpus scan cycle for this account. `0` or absent defaults to 500. Lower it on a slow link or server; raise it to catch up faster on a fast one. |
| `rules` | no | List of manually-configured rules for this account (see [Matchers and actions](#matchers-and-actions)). Absent/empty means only learned rules (if any) apply. |
| `learningShortcuts` | no | Named flat `mom-rules/<name>` folders bound to a fixed (matcher type, action) pair, as an alternative to the full discovery tree. See [Learning shortcuts](#learning-shortcuts). |
| `discoveryTreeDisabled` | no | Skips creating/maintaining the `<MATCHER_TYPE>/<ACTION_TYPE>` discovery tree under `mom-rules/` entirely. `mom-rules/Done` and any `learningShortcuts` folders are unaffected. Default `false`. Useful with a mail client that shows every IMAP folder unconditionally regardless of subscription state (e.g. Apple Mail), once shortcuts cover what's actually used. See [Learning shortcuts](#learning-shortcuts). |

Connections are always made over IMAPS (implicit TLS) — there is no plain-IMAP option.

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
ones to `SpamML` left unread for review. Two rules at two thresholds rather than
one: [`SUBJECT_CLASSIFIER_EQUALS`](matchers/subject-classifier-equals.md) only ever
matches/doesn't match, so "confident" vs "unsure" is expressed as two separately-configured
rules, not a single rule with a confidence level.

Note the classifier's verdicts go to `SpamML`, a folder of its own, **not** straight into
`Spam` — and `classifierExcludedFolders` excludes it from corpus collection. See
[Excluding a folder from the corpus](#excluding-a-folder-from-the-corpus) for why: routing it
into `Spam` unexcluded would let the classifier train on its own past verdicts.

The `FCRDNS_RESULT_EQUALS: none` rule is here mainly to show the syntax — see
[its own doc](matchers/fcrdns-result-equals.md) before using it standalone like this in
production: it's a much weaker signal than SPF/DKIM/DMARC (plenty of legitimate small mail
servers have no forward-confirmed reverse DNS), so it's usually better combined with another
weak signal via `AND` than acted on alone.

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

## Matchers and actions

A rule is a `matcher` + an `action`. When a matcher matches a message, its action runs.

**Matchers** (`net.pieroxy.mom.rules.matchers`):

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
| [`SUBJECT_CLASSIFIER_EQUALS`](matchers/subject-classifier-equals.md) | Match when a locally-trained model scores the subject above/below a threshold. |
| [`HEADER_CLASSIFIER_EQUALS`](matchers/header-classifier-equals.md) | Match when a locally-trained model scores a message's headers above/below a threshold. |
| [`BODY_CLASSIFIER_EQUALS`](matchers/body-classifier-equals.md) | Match when a locally-trained model scores a message's body text above/below a threshold. |
| [`IP_REPUTATION_EQUALS`](matchers/ip-reputation-equals.md) | Match when the connecting IP's reputation score (from downloaded lists) crosses a threshold. |
| [`FROM_DOMAIN_REPUTATION_EQUALS`](matchers/from-domain-reputation-equals.md) | Same as above, on the `From:` domain against domain reputation lists. |
| [`AND`](matchers/and.md) | Composite: all children must match. |
| [`OR`](matchers/or.md) | Composite: any child matching is enough. |
| [`NOT`](matchers/not.md) | Composite: matches if its single child does not. |

**Actions** (`net.pieroxy.mom.rules.actions`):

| Type | Purpose |
|---|---|
| [`MOVE_TO`](actions/move-to.md) | Move the message to another folder. |
| [`READ`](actions/read.md) | Mark the message as read. |
| [`MOVE_TO_AND_READ`](actions/move-to-and-read.md) | Mark as read, then move. |
| [`AND`](actions/and.md) | Composite: run children in order, stop at first failure. |
| [`OR`](actions/or.md) | Composite: run children in order, stop at first success. |
| [`NOOP`](actions/noop.md) | Does nothing, always succeeds — for observing a match without acting on it. |

Every matcher and action config accepts an optional `logLevel` field (`"DEBUG"`, `"INFO"`,
`"WARNING"`, or `"ERROR"`; default `"INFO"`) controlling **only that node's own** log verbosity
— it never affects sibling or child nodes. `"DEBUG"` maps to `java.util.logging`'s `FINE` level
(there is no native `DEBUG` level in `java.util.logging`). See [Logging](#logging).

Composite matchers/actions (`AND`/`OR`/`NOT`) take a `children` array of nested matcher/action
configs instead of `key`/`keys`.

## Rule evaluation order

For each new message, MOM walks the rules in `config.json`, in the order they appear, and applies
the **first one whose matcher matches**. A rule "applies" (and stops the search) as soon as its
matcher matches, even if the action itself later fails; a failed action is logged but doesn't
make MOM try the next rule instead.

Learned rules always get a chance to run too, but *where* depends on `config.json`:

* By default (no `LEARNED_RULES` entry anywhere in `rules`), they run **implicitly, after every
  manually-configured rule** — the original, and still simplest, behavior.
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

Defaults to `false` (existing behavior, unchanged) if absent.

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
`NOT` types, which are reserved for manual config). Each cycle, for every example message found:

1. The matcher's key is extracted from the example (e.g. its sender's domain).
2. The rule is persisted to `<dataFolder>/<displayName>-learned-rules.json` — a separate file
   from `config.json`, which MOM never modifies. If a rule with the same matcher type and
   action already exists, the new key is merged into it (`keys`) rather than creating a
   duplicate rule.
3. The action actually runs on the example message itself.
4. If the example is still present afterward (the action didn't move or delete it), it's moved
   to `mom-rules/Done` — so it isn't re-learned/re-run every cycle. It's marked **unread** on
   the way in, regardless of its state before: a clear indicator in the mail client that
   something there still needs to be manually sorted.

Learned rules always come **after** manual rules in evaluation order, so a manual rule always
wins if both would match.

### Learning shortcuts

The full discovery tree above creates one `<MATCHER_TYPE>/<ACTION_TYPE>` folder pair for
**every** learnable combination — useful to see what's possible, but in practice most accounts
only ever use a handful of them, and subscribing an IMAP client to the rest just for them to sit
there unused doesn't scale.

`learningShortcuts` (per account) gives a specific (matcher type, action) pair its own flat
folder directly under `mom-rules/`, instead of the two nested levels:

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
`mom-rules/FROM_DOMAIN_EQUALS/MOVE_TO_AND_READ/Spam` would — same extraction, same persistence
to `<displayName>-learned-rules.json`, same `Done` archival — just reached through one
subscribable folder instead of two unsubscribed levels. The two mechanisms coexist freely: a
shortcut doesn't remove its equivalent discovery-tree folder, so nothing stops using one to
explore and the other day to day.

The `action` is fully fixed in config, `key` included — a shortcut has no `<key>` folder level
left to carry a destination, unlike the discovery tree. `matcher` only ever takes a `type`: its
key is still extracted from each example, exactly as in the discovery tree, so setting `key` (or
`keys`) on a shortcut's matcher is rejected. MOM validates every shortcut at startup — a
non-learnable matcher/action type, a missing `action.key`, a matcher `key`/`keys`, a name reused
by two shortcuts, or a name colliding with a discovery-tree folder (a `MATCHER_TYPE` name, or
`Done`) all fail loudly rather than being silently ignored.

Subscribing an IMAP client to just the shortcuts (and not the discovery tree) works for clients
that respect the IMAP-standard subscribed/unsubscribed state — but some (notably Apple Mail, on
both iOS and macOS) show every folder regardless of that state. For those, `discoveryTreeDisabled`
(per account) skips creating the tree in the first place, once shortcuts cover what's actually
used day to day.

## Manually reprocessing a message

To re-run the full rule catalog against a message that's already in your mailbox (for example,
after adding a rule that should have caught it), drop it into `mom-rules/ToProcess`. Every
cycle, MOM treats every message found there exactly as if it had just arrived: the first
matching rule applies normally. If the message is still present afterward — no rule matched, or
the matching rule's action didn't relocate it — it's moved to `mom-rules/Done` (the same folder
used by the learning system above, marked unread the same way) for manual review.

## Logging

Console logging is always on. MOM also always writes to `<dataFolder>/logs/log.txt`, rotating
daily into lz4-compressed archives (kept for `keepLogFiles` days) if `keepLogFiles > 0`.

Each matcher/action node in a rule has its own logger, independently leveled via that node's
`logLevel` config field (default `INFO`). Setting `"logLevel": "DEBUG"` on an
[`SPF_RESULT_EQUALS`](matchers/spf-result-equals.md), [`DKIM_RESULT_EQUALS`](matchers/dkim-result-equals.md),
or [`DMARC_RESULT_EQUALS`](matchers/dmarc-result-equals.md) matcher is particularly useful: it
surfaces that rule's full live verification trace (DNS records fetched, mechanisms/signatures
evaluated, alignment computed) without touching any global logging configuration.

### Stats log

Besides the human-readable log above, MOM appends one JSON line per event to
`<dataFolder>/logs/stats-YYYY-MM-DD.json` (UTC day). Two event types, told apart by `type`:

```json
{"type":"MATCH","matcher":"IpReputationMatcher[spamhaus-drop](score=0.87)","date":"2026-09-18 10:35:12Z"}
{"type":"PROCESSED","result":"MATCH","matcher":"IpReputationMatcher[spamhaus-drop](score=0.87)","processedMs":42,"date":"2026-09-18 10:35:12Z"}
{"type":"PROCESSED","result":"PASS","processedMs":7,"date":"2026-09-18 10:36:01Z"}
```

- **`MATCH`** — one per rule that actually matched (manual or learned), same moment as the
  matching line in the console/`log.txt` (see [Rule evaluation order](#rule-evaluation-order)).
  `matcher` is that same description, including any per-message detail a matcher reports (a
  reputation score, a classifier score, which of several configured keys hit...). A message
  processed by a `keepProcessing` rule followed by a blocking one produces two `MATCH` lines —
  one for each rule that matched, in evaluation order.
- **`PROCESSED`** — exactly one per message, once the whole rule chain (manual rules, then
  learned rules) has finished running for it. `processedMs` is how long that took; `result` is
  `MATCH` if some rule ultimately blocked the message — `matcher` then names that rule (same
  description as its own separate `MATCH` line above; deliberately redundant so the common case,
  a single non-`keepProcessing` rule blocking, is fully readable from this one line alone) — or
  `PASS` if the chain ran to completion without ever blocking (no `matcher` field), even if a
  `keepProcessing` rule matched somewhere along the way (that rule's own `MATCH` line is the only
  record of it). This is the one to use for throughput/latency: count `PROCESSED` lines for
  messages handled, average `processedMs` for how fast, and the `PASS`/`MATCH` split of `result`
  for how much traffic gets filtered.

One JSON object per line (not one JSON array for the whole file), so recording an event is a
plain append, never a rewrite of the whole day's file — safe with several accounts running
concurrently. This file has no rotation or pruning of its own — clean up old `stats-*.json`
files yourself if needed.

## Data files

Everything MOM persists lives under `dataFolder`, one file/folder per account (keyed by
`displayName`):

| Path | Contents |
|---|---|
| `logs/log.txt` | Current log file (see [Logging](#logging)); rotated into `logs/log.txt.N.lz4` archives. |
| `logs/stats-YYYY-MM-DD.json` | One JSON-lines file per UTC day of successful rule matches (see [Stats log](#stats-log)). Not rotated/pruned by MOM. |
| `<displayName>.json` | INBOX UID cursor (which messages have already been processed). |
| `<displayName>-learned-rules.json` | Rules learned via `mom-rules/` (see above). Hand-editable. |
| `classifier-corpus/<displayName>-scan-state.json` | Per-folder UID cursor for corpus scanning. |
| `classifier-corpus/<displayName>/classifier-YYYY-MM-DD.json.lz4` | One compressed file per day of collected corpus data. |
| `classifier-corpus/<displayName>/subject-model.bin` | Trained subject classifier model (see below). Absent until enough data has been collected. |
| `classifier-corpus/<displayName>/header-model.bin` | Trained header classifier model (see below). Absent until enough data has been collected. |
| `classifier-corpus/<displayName>/body-model.bin` | Trained body classifier model (see below). Absent until enough data has been collected. |

## Classifier corpus collection

When `classifierCorpusRetentionDays > 0`, MOM builds a labeled dataset per account under
`dataFolder`, walking every folder except `INBOX` and `mom-rules/` and labeling messages `SPAM`
(if in the `classifierSpamFolderName` folder) or `HAM` (everything else). Two different scan
rhythms:

- **The `classifierSpamFolderName` folder** is scanned **every cycle** (the same cadence as
  normal INBOX processing). This is deliberate: it's the one folder a user might empty
  themselves before the next daily scan would otherwise have seen it (e.g. purging Spam by hand
  every evening) — scanning it every cycle means a message is captured for the corpus as soon as
  it arrives, not whenever the next daily pass happens to fall.
- **Every other folder** is scanned **once a day**, since none of them are at similar risk of
  being emptied out from under the scanner, and walking the whole account tree every cycle would
  be wasteful.

Files older than `classifierCorpusRetentionDays` days are pruned once a day.

A message received more than `classifierCorpusRetentionDays` days ago (per the server's own
INTERNALDATE, not the sender's self-reported `Date:` header) is skipped during the scan itself,
not just pruned afterward — it would just get pruned right away otherwise, so there's no point
fetching and storing it in the first place. This matters most on a folder's very first scan (e.g.
a years-old Archive or Sent folder): without it, the scan would spend its whole per-cycle budget
walking through history that's already outside the retention window before ever reaching
anything the corpus will actually keep.

### Excluding a folder from the corpus

`classifierExcludedFolders` skips a folder (matched by name, anywhere in the tree) entirely —
neither `SPAM` nor `HAM`. This matters most if a
[`SUBJECT_CLASSIFIER_EQUALS`](matchers/subject-classifier-equals.md),
[`HEADER_CLASSIFIER_EQUALS`](matchers/header-classifier-equals.md), or
[`BODY_CLASSIFIER_EQUALS`](matchers/body-classifier-equals.md) rule moves its own verdicts
into a dedicated folder (e.g. `"SpamML"`, or a `"Spam/ML"` subfolder) rather than straight into
`classifierSpamFolderName`:

- **Without exclusion, that folder would be actively mislabeled**, not just ignored: since its
  name doesn't match `classifierSpamFolderName`, the scanner would label everything in it `HAM`
  — poisoning the corpus with spam classified as legitimate mail, worse than not learning from it
  at all.
- It also breaks a feedback loop: without exclusion, the classifier would eventually train on its
  own past verdicts, letting an early mistake reinforce itself. A rule whose action moves mail
  into `classifierSpamFolderName` directly doesn't have this problem — a human- or
  protocol-verified rule (SPF/DKIM/DMARC/`FROM_*`) landing mail in Spam is still a genuine,
  independent signal worth learning from; it's specifically the classifier grading its own
  homework that's excluded here.

```json
{ "classifierExcludedFolders": ["SpamML"] }
```

### Subject classifier training

Once a day, right after a full scan completes, MOM (re)trains a subject-only spam classifier
(Naive Bayes) on the entire retained corpus, and writes it to
`classifier-corpus/<displayName>/subject-model.bin`. Training is
skipped — logged, not an error — until there are **at least 50 examples of each class** (`SPAM`
and `HAM`); a lopsided or too-small corpus produces a model that hasn't learned anything useful,
so MOM doesn't bother writing one yet.

Use [`SUBJECT_CLASSIFIER_EQUALS`](matchers/subject-classifier-equals.md) in a rule to actually
act on this — see its doc for the config format (a probability threshold like `">0.9"`, not a
value to match) and how it behaves before a model exists.

### Header classifier training

Same rhythm as the subject classifier above, but a separate model over a different set of
features — derived from headers and MIME structure (sender/recipient domains, `In-Reply-To`,
`List-Id`, `Precedence`, `Return-Path`/`Reply-To` alignment with `From`, attachment count and
filename extensions...) — written to `classifier-corpus/<displayName>/header-model.bin`. Same
minimum (**at least 50 examples of each class**) before it bothers writing a model.

Use [`HEADER_CLASSIFIER_EQUALS`](matchers/header-classifier-equals.md) in a rule to act on this.
Nothing ties the classifiers together — each can be used alone, together, or compared against
the others (e.g. via `keepProcessing`, see [Rule evaluation order](#rule-evaluation-order)).

### Body classifier training

Same rhythm as the other two, but trained on the message body's visible text — the HTML part
stripped down to text (tags, scripts, and styles removed via [Jsoup](https://jsoup.org/)), or a
`text/plain` part used as-is when no HTML part exists at all — written to
`classifier-corpus/<displayName>/body-model.bin`. Bag-of-words like the subject classifier (free
text, not structured facts), plus one extra feature for which kind of part the body came from
(`html`, `plain`, or absent for a message with no body at all, e.g. image-only or
attachment-only) — a signal a bag-of-words model can't otherwise recover, since stripping HTML
down to text makes it indistinguishable from a plain-text part with the same wording.

Two training parameters are more conservative here than for the subject/header classifiers,
because body text's vocabulary is much larger than a subject line's: training is skipped until
there are **at least 150 examples of each class** (not 50), and words occurring fewer than 3
times across the whole corpus are dropped rather than kept (subject/header keep every word,
however rare). Both guard against the same risk — a word seen once or twice (a name, a URL, a
one-off phrase) getting memorized as if it generalized, rather than genuinely learning what
distinguishes spam from ham.

Body text isn't truncated: kept in full, on purpose, until real corpus volume shows what length
is actually worth capping.

Use [`BODY_CLASSIFIER_EQUALS`](matchers/body-classifier-equals.md) in a rule to act on this.

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

Every list present in `reputationLists` is downloaded and refreshed on this schedule, whether or
not a matcher currently references it. A signal is available from the very first message: the
last downloaded copy is cached locally and reloaded before any network activity happens.

A list only actually re-downloads once `refreshHours` has genuinely elapsed since its last
successful download. Every download attempt, success or failure, is logged.

Blank lines are ignored; `#` or `;` start a comment, either as a whole line or trailing after an
entry (e.g. Spamhaus DROP publishes `1.10.16.0/20 ; SBL256894` — the `; SBL256894` reference is
stripped, not treated as part of the entry). A single malformed entry is skipped (logged) rather
than failing the whole list. If a refresh fails (the source is down, network issue...), MOM keeps
serving the last successfully downloaded copy indefinitely, rather than losing the signal.

See [Starter configuration](../README.md#starter-configuration) for the five free sources wired
up by default (two IP feeds combined via `AND`/`OR`, two domain feeds likewise, plus one
disposable-email list) and why. See
[Reputation list pairing rationale](IMPLEMENTATION-DETAILS.md#reputation-list-pairing-andor-rationale)
for the design reasoning if you're adding your own.

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

The UI itself is a static single-page app, built from `src/webapp` and embedded into the release
jar — it's extracted fresh to `<dataFolder>/webapp-ui` on every startup, so an upgraded jar never
serves a stale UI. Responses above 1KB are gzip-compressed automatically.

The current UI is a placeholder ("Hello World") — the real UI and its API are still to come.
