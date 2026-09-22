# Matcher: `BODY_CLASSIFIER_EQUALS`

[← back to docs](../README.md)

Matches when a message's body text is scored above or below a threshold by a locally-trained
classifier — same corpus and training rhythm as
[`SUBJECT_CLASSIFIER_EQUALS`](subject-classifier-equals.md) (see
[Classifier corpus collection](../README.md#classifier-corpus-collection)), but trained on the
body's visible text instead of the `Subject:` line. Runs independently of
`SUBJECT_CLASSIFIER_EQUALS`/[`HEADER_CLASSIFIER_EQUALS`](header-classifier-equals.md) — nothing
stops all three from being configured at once, e.g. to compare them (see
[`keepProcessing`](../README.md#rule-evaluation-order) for letting a comparison rule run
alongside the real one without blocking it).

## What "body text" means here

The message's HTML part, tags/scripts/styles stripped down to plain text — that's what a human
actually sees when they open the message. A `text/plain` alternative part, when present
alongside HTML, is ignored: in practice it's rarely more than an unread fallback. It's only used,
as-is, when the message has no HTML part at all. A message with neither (image-only or
attachment-only) has no body text; the model still classifies it, using where the body came from
(`html`, `plain`, or absent) as a feature of its own rather than skipping the message — that
distinction matters because it's exactly what stripping HTML down to text erases: an HTML message
and a plain-text one with identical wording would otherwise look the same to the model.

## Config fields

| Field | Required | Description |
|---|---|---|
| `key` | yes | A comparison, not a value to match: an operator (`>`, `>=`, `<`, or `<=`) immediately followed by a number between 0 and 1, e.g. `">0.9"` or `"<=0.05"`. |
| `logLevel` | no | See [Logging](../README.md#logging). |

There is no `keys` for this matcher — a threshold is a single comparison, not a set of values to
match against.

## Not learnable by example

Unlike the other leaf matchers, dropping an example into `mom-rules/` does nothing for this
type — it isn't in the learnable list. There's no per-example "key" to extract (the config field
here is a threshold, not a value pulled from a message), and the model isn't trained from
individual dropped examples anyway: it's retrained from the whole classifier corpus once a day
(see [Classifier corpus collection](../README.md#classifier-corpus-collection)).

## Confident vs. unsure: two rules, not one

This matcher only ever matches or doesn't — there's no notion of "matched, but only a little" in
its result. To act differently depending on confidence (e.g. auto-file very confident spam, but
only flag unsure spam for review), configure **two separate rules at two different thresholds**,
each with its own action, rather than looking for a single "confidence level" setting:

```json
[
  {
    "matcher": { "type": "BODY_CLASSIFIER_EQUALS", "key": ">0.99" },
    "action": { "type": "MOVE_TO_AND_READ", "key": "Spam" }
  },
  {
    "matcher": { "type": "BODY_CLASSIFIER_EQUALS", "key": ">0.5" },
    "action": { "type": "MOVE_TO", "key": "Spam" }
  }
]
```

Rule evaluation stops at the first match (see [Rule evaluation order](../README.md#rule-evaluation-order)),
so list the stricter threshold first: a message scoring `0.995` matches the first rule and stops
there; one scoring `0.7` skips it and matches the second.

The operator can also point the other way — e.g. `"<0.1"` to match messages the model is
confident are *not* spam, useful for a rule that should only apply to mail the classifier is
comfortable calling legitimate.

## Behavior before a model exists

Until training has produced a model — either because too little data has been collected yet, or
the process just started — this matcher never matches. It logs this at `INFO` **once**, not on
every message inspected:

```
INFO: BodyClassifierMatcher inactive: no trained model yet for this account (not enough data collected so far)
```

Once a model appears (or a newer one replaces it after a later training run), the matcher
notices on its own — no restart needed, unlike hand-edited learned rules (see
[`SUBJECT_STARTS_WITH`](subject-starts-with.md#learning-by-example--read-this-before-using-it)
for a case where a restart *is* required).

## Example

```json
{
  "matcher": { "type": "BODY_CLASSIFIER_EQUALS", "key": ">0.9" },
  "action": { "type": "MOVE_TO", "key": "Spam" }
}
```
