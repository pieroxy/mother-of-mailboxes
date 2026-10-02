# Action: `NOOP`

[← back to docs](../README.md)

Does nothing to the message; always succeeds. Useful for a rule that only exists to be observed
— e.g. logging that a matcher would have matched, or comparing one matcher's verdict against
another's — without actually moving or flagging the mail. Without `keepProcessing`, a match still
stops evaluation, which makes it an allow-list entry for the rules after it.

## Config fields

| Field | Required | Description |
|---|---|---|
| `key` | no | Free-text label, logged when the rule applies. Required in a learning shortcut. |
| `logLevel` | no | See [Logging](../README.md#logging). |

## Behavior

- Touches nothing on the message. Always reports success.
- Learnable: dropping an example message into `mom-rules/<MATCHER_TYPE>/NOOP/<key>` (or a
  learning shortcut) teaches a rule that stops evaluation for that sender. The example itself is
  left untouched, so it's filed into `mom-rules/Done` — move it back to the inbox by hand.

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

Allow-listing domains that fail SPF: a learning shortcut teaches the allow-list, and
`LEARNED_RULES` is placed before the SPF check so learned rules get evaluated first (see
[Rule evaluation order](../README.md#rule-evaluation-order)):

```json
"rules": [
  { "type": "LEARNED_RULES" },
  { "matcher": { "type": "SPF_RESULT_EQUALS", "key": "fail" }, "action": { "type": "MOVE_TO_AND_READ", "key": "Spam" } }
],
"learningShortcuts": [
  {
    "name": "DomainWhitelisted",
    "matcher": { "type": "FROM_DOMAIN_EQUALS" },
    "action": { "type": "NOOP", "key": "Whitelisted" }
  }
]
```

This moves *every* learned rule ahead of the SPF check, not just the allow-list.
