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

Not learnable by example: the models are trained from your folders, see
[Classifier corpus collection](../README.md#classifier-corpus-collection).

## Confident vs. unsure: two rules, not one

To act differently depending on confidence, use two rules at two thresholds:

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

List the stricter threshold first, since evaluation stops at the first match (see
[Rule evaluation order](../README.md#rule-evaluation-order)). A threshold like `"<0.1"` matches
the subjects the model is confident are *not* spam.

## Before a model exists

Until the first model is trained (see the minimum number of examples in
[Classifier corpus collection](../README.md#classifier-corpus-collection)), this matcher never
matches. New models are picked up without a restart.

## Example

```json
{
  "matcher": { "type": "SUBJECT_CLASSIFIER_EQUALS", "key": ">0.9" },
  "action": { "type": "MOVE_TO", "key": "Spam" }
}
```
