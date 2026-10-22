# Matcher: `BODY_CLASSIFIER_EQUALS`

[← back to docs](../README.md)

Matches when a message's body text is scored above or below a threshold by a locally-trained
classifier. Like [`SUBJECT_CLASSIFIER_EQUALS`](subject-classifier-equals.md) and
[`HEADER_CLASSIFIER_EQUALS`](header-classifier-equals.md), but a separate model: use any of them,
alone or together.

The body text is what's shown when the message is opened: the HTML part's text, or the plain
text part when there's no HTML.

## Config fields

| Field | Required | Description |
|---|---|---|
| `key` | yes | A comparison, not a value to match: an operator (`>`, `>=`, `<`, or `<=`) immediately followed by a number between 0 and 1, e.g. `">0.9"` or `"<=0.05"`. |
| `logLevel` | no | See [Logging](../logs.md#application-log). |

Not learnable by example: the models are trained from your folders, see
[Classifier corpus collection](../classifiers.md#classifier-corpus-collection).

## Confident vs. unsure: two rules, not one

To act differently depending on confidence, use two rules at two thresholds:

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

List the stricter threshold first, since evaluation stops at the first match (see
[Rule evaluation order](../rules.md#rule-evaluation-order)). A threshold like `"<0.1"` matches
the messages the model is confident are *not* spam.

## Before a model exists

Until the first model is trained (see the minimum number of examples in
[Classifier corpus collection](../classifiers.md#classifier-corpus-collection)), this matcher never
matches. New models are picked up without a restart.

## Example

```json
{
  "matcher": { "type": "BODY_CLASSIFIER_EQUALS", "key": ">0.9" },
  "action": { "type": "MOVE_TO", "key": "Spam" }
}
```
