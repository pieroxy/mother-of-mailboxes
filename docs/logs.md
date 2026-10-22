# Logs and data files

[← back to docs](README.md)

## Application log

Console logging is always on. MOM also always writes to `<dataFolder>/logs/log.txt`, rotated at
midnight into lz4-compressed archives (`log.txt.1.lz4` being the most recent), kept for
`keepLogFiles` days. Browse them from the web UI: the Logs icon in the top bar.

Each matcher/action node in a rule has its own logger, independently leveled via that node's
`logLevel` config field (default `INFO`). Setting `"logLevel": "DEBUG"` on an
[`SPF_RESULT_EQUALS`](matchers/spf-result-equals.md), [`DKIM_RESULT_EQUALS`](matchers/dkim-result-equals.md),
or [`DMARC_RESULT_EQUALS`](matchers/dmarc-result-equals.md) matcher is particularly useful: it
surfaces that rule's full live verification trace (DNS records fetched, mechanisms/signatures
evaluated, alignment computed) without touching any global logging configuration.

## Access log

When the [web server](configuration.md#web-server) is on, every HTTP request it answers (API, static files, unknown
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

## Stats log

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

## Decision log

Opt-in per account via `decisionLogRetentionDays`: one JSON line per message processed, in
`<dataFolder>/logs/<displayName>/decisions-YYYY-MM-DD.json` (UTC day), explaining where it ended
up and why. Browse it from the web UI: the Logs icon on the account's page, or a matcher on the
stats page.

```json
{"date":"2026-10-13 10:00:00Z","trigger":"INBOX","messageId":"<abc@example.com>","subject":"Hello","from":"Alice <alice@example.com>","processedMs":42,
 "rules":[{"rule":"Rule(...)","learnedRule":false,"matched":false},
          {"rule":"Rule(...)","ruleName":"Spamhaus","learnedRule":false,"matched":true,"matchDetail":"IpReputationMatcher[spamhaus-drop](score=0.87)","actionName":"→ Spam","spam":true,"keepProcessing":false}]}
```

- `trigger`: `INBOX`, `MANUAL_REPROCESSING` (see [Manually reprocessing a message](learning.md#manually-reprocessing-a-message)),
  or `LEARNING` (an example dropped in `mom-rules/`: `learnedRule` names the rule learned,
  `reasonForMoveToDone` why it was filed to `mom-rules/Done`, and there's no `rules` array).
- `rules`: every rule reached, in evaluation order; rules after the one that stopped evaluation
  never ran and aren't listed. `matched` is absent if the rule threw before deciding (see
  `exception`). `ruleName`, `actionName` and `spam` are the rule's and action's `name`/`spam` at
  the time (see [Matchers and actions](rules.md#matchers-and-actions)); renaming them later doesn't
  rewrite past entries.

Unlike the stats log, it carries mail metadata (Subject, From, Message-ID), so it's rotated:
past days are lz4-compressed, and days older than `decisionLogRetentionDays` deleted.

## Data files

Everything MOM persists lives under `dataFolder`, one file/folder per account (keyed by
`displayName`):

| Path | Contents |
|---|---|
| `logs/log.txt` | Current log file (see [Logging](#application-log)); rotated into `logs/log.txt.N.lz4` archives. |
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
