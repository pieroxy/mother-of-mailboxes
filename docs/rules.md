# Rules

[← back to docs](README.md)

A rule is a matcher (when) and an action (then). For each new message, rules are tried in
order, and **the first one that matches wins**: its action runs and evaluation stops there.

SPF, DKIM, DMARC and FCrDNS are always checked by MOM itself, never read from headers already on
the message, since a sender can forge those.

## Matchers and actions

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
— it never affects sibling or child nodes. See [Logging](logs.md#application-log).

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

For each new message, MOM walks the rules in order (as listed in `config.json`, or in the account
settings of the web UI), and applies the **first one whose matcher matches**. A rule "applies" (and stops the search) as soon as its
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
