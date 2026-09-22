# Action: `NOOP`

[← back to docs](../README.md)

Does nothing to the message; always succeeds. Useful for a rule that only exists to be observed
— e.g. logging that a matcher would have matched, or comparing one matcher's verdict against
another's — without actually moving or flagging the mail.

## Config fields

| Field | Required | Description |
|---|---|---|
| `logLevel` | no | See [Logging](../README.md#logging). No `key` needed. |

## Behavior

- Touches nothing on the message. Always reports success.
- Not learnable: there's no folder/key to speak of, and "learn me a rule that does nothing"
  isn't a meaningful example to drop into `mom-rules/`.

## Example

Comparing [`HEADER_CLASSIFIER_EQUALS`](../matchers/header-classifier-equals.md) against
[`SUBJECT_CLASSIFIER_EQUALS`](../matchers/subject-classifier-equals.md) without letting either
one act on the mail, while the real rules further down the list still run — combine with
`"keepProcessing": true` (see [Rule evaluation order](../README.md#rule-evaluation-order)):

```json
{
  "matcher": { "type": "HEADER_CLASSIFIER_EQUALS", "key": ">0.9", "logLevel": "INFO" },
  "action": { "type": "NOOP" },
  "keepProcessing": true
}
```
