# Matcher: `FROM_ADDRESS_EQUALS`

[← back to docs](../README.md)

Matches when the sender's email address (just `local@domain`, ignoring any display name) equals
the configured value. **Case-insensitive.**

## Config fields

| Field | Required | Description |
|---|---|---|
| `key` | one of `key`/`keys` | Email address to match, e.g. `"jdupont@example.com"`. |
| `keys` | one of `key`/`keys` | Set of addresses, any of which matches. Takes priority over `key` if both are set. |
| `logLevel` | no | See [Logging](../README.md#logging). |

## Behavior

- Only matches messages with **exactly one** `From:` address. A message with zero or multiple
  From addresses never matches (logged at `FINE`).
- Extracts the address part via `InternetAddress.getAddress()`, so display name and any quoting
  are ignored — `"Jean Dupont <jdupont@example.com>"` and `jdupont@example.com` match the same
  key.
- Learnable: dropping an example message into
  `mom-rules/FROM_ADDRESS_EQUALS/<ACTION_TYPE>/<key>` teaches a rule using that message's
  sender address as the key. Fails to learn from a message with zero or multiple From addresses,
  or one with no usable email part.

## Example

Match any of a few trusted addresses without caring how their display name is formatted:

```json
{
  "matcher": { "type": "FROM_ADDRESS_EQUALS", "keys": ["boss@example.com", "hr@example.com"] },
  "action": { "type": "READ" }
}
```
