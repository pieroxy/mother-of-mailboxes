# Matcher: `FROM_EQUALS`

[← back to docs](../README.md)

Matches when the message's `From:` header, rendered exactly as `"Display Name <address>"` (or
just `address` if there's no display name), equals the configured value. **Case-sensitive.**

## Config fields

| Field | Required | Description |
|---|---|---|
| `key` | one of `key`/`keys` | Exact `From:` string to match. |
| `keys` | one of `key`/`keys` | Set of values, any of which matches. Takes priority over `key` if both are set. |
| `logLevel` | no | See [Logging](../README.md#logging). |

## Behavior

- Only matches messages with **exactly one** `From:` address. A message with zero or multiple
  From addresses never matches (logged at `FINE`).
- Comparison is a plain `String.equals` — no trimming, no case folding. If you're not sure of
  the exact display name a sender uses, prefer [`FROM_ADDRESS_EQUALS`](from-address-equals.md).
- Learnable: dropping an example message into
  `mom-rules/FROM_EQUALS/<ACTION_TYPE>/<key>` teaches a rule using that message's exact `From:`
  string as the key. Fails to learn from a message with zero or multiple From addresses.

## Example

```json
{
  "matcher": { "type": "FROM_EQUALS", "key": "Jean Dupont <jdupont@example.com>" },
  "action": { "type": "READ" }
}
```
