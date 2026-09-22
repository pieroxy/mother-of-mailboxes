# Matcher: `FROM_DOMAIN_EQUALS`

[← back to docs](../README.md)

Matches when the sender's domain (the part after `@`) equals the configured value.
**Case-insensitive.** Useful for routing or blocking a whole domain rather than one address.

## Config fields

| Field | Required | Description |
|---|---|---|
| `key` | one of `key`/`keys` | Domain to match, e.g. `"hotmail.com"`. |
| `keys` | one of `key`/`keys` | Set of domains, any of which matches. Takes priority over `key` if both are set. |
| `logLevel` | no | See [Logging](../README.md#logging). |

## Behavior

- Only matches messages with **exactly one** `From:` address. A message with zero or multiple
  From addresses never matches (logged at `FINE`).
- Note that a domain alone says nothing about whether the mail is actually authorized to be sent
  from that domain — a `From:` header is trivial to forge. Combine with
  [`SPF_RESULT_EQUALS`](spf-result-equals.md) or [`DKIM_RESULT_EQUALS`](dkim-result-equals.md)
  via an `AND` matcher if you need that guarantee (see the example in the
  [main config example](../README.md#example-configjson)).
- Learnable: dropping an example message into
  `mom-rules/FROM_DOMAIN_EQUALS/<ACTION_TYPE>/<key>` teaches a rule using that message's sender
  domain as the key. Fails to learn from a message with zero or multiple From addresses, or one
  with no domain part.

## Example

```json
{
  "matcher": { "type": "FROM_DOMAIN_EQUALS", "key": "newsletter.example.com" },
  "action": { "type": "MOVE_TO", "key": "Newsletters" }
}
```
