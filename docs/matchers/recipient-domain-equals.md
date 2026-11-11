# Matcher: `RECIPIENT_DOMAIN_EQUALS`

[← back to docs](../README.md)

Matches when any recipient's domain (the part after `@`) equals the configured value.
**Case-insensitive**, subdomains excluded. Useful when one mailbox receives the mail of several
domains.

## Config fields

| Field | Required | Description |
|---|---|---|
| `key` | one of `key`/`keys` | Domain to match, e.g. `"example.org"`. |
| `keys` | one of `key`/`keys` | Set of domains, any of which matches. Takes priority over `key` if both are set. |
| `logLevel` | no | See [Logging](../logs.md#application-log). |

## Behavior

- Same recipients as [`RECIPIENT_ADDRESS_EQUALS`](recipient-address-equals.md).
- Learnable, the same way: the key is the domain of the account's addresses among the recipients,
  which must all share it.

## Example

```json
{
  "matcher": { "type": "RECIPIENT_DOMAIN_EQUALS", "key": "association.example.org" },
  "action": { "type": "MOVE_TO", "key": "Association" }
}
```
