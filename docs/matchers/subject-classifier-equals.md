# Matcher: `SUBJECT_CLASSIFIER_EQUALS`

[← back to docs](../README.md)

Matches when the message's `Subject:` is scored above or below a threshold by a locally-trained
classifier — see [Classifier corpus collection](../README.md#classifier-corpus-collection) for
how that model is built.

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
    "matcher": { "type": "SUBJECT_CLASSIFIER_EQUALS", "key": ">0.99" },
    "action": { "type": "MOVE_TO_AND_READ", "key": "Spam" }
  },
  {
    "matcher": { "type": "SUBJECT_CLASSIFIER_EQUALS", "key": ">0.5" },
    "action": { "type": "MOVE_TO", "key": "Spam" }
  }
]
```

Rule evaluation stops at the first match (see [Rule evaluation order](../README.md#rule-evaluation-order)),
so list the stricter threshold first: a subject scoring `0.995` matches the first rule and stops
there; one scoring `0.7` skips it and matches the second.

The operator can also point the other way — e.g. `"<0.1"` to match subjects the model is
confident are *not* spam, useful for a rule that should only apply to mail the classifier is
comfortable calling legitimate.

## Behavior before a model exists

Until [training](../README.md#subject-classifier-training) has produced a model — either because
too little data has been collected yet, or the process just started — this matcher never
matches. It logs this at `INFO` **once**, not on every message inspected:

```
INFO: SubjectClassifierMatcher inactive: no trained model yet for this account (not enough data collected so far)
```

Once a model appears (or a newer one replaces it after a later training run), the matcher
notices on its own — no restart needed, unlike hand-edited learned rules (see
[`SUBJECT_STARTS_WITH`](subject-starts-with.md#learning-by-example--read-this-before-using-it)
for a case where a restart *is* required).

## Example

```json
{
  "matcher": { "type": "SUBJECT_CLASSIFIER_EQUALS", "key": ">0.9" },
  "action": { "type": "MOVE_TO", "key": "Spam" }
}
```
