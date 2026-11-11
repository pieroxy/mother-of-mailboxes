# Matcher: `RECIPIENT_ADDRESS_EQUALS`

[← back to docs](../README.md)

Matches when any recipient's address equals the configured value. **Case-insensitive.** Useful
for routing by the address a message was sent to: an alias, a plus address, a catch-all address.

## Config fields

| Field | Required | Description |
|---|---|---|
| `key` | one of `key`/`keys` | Address to match, e.g. `"shop@example.com"`. |
| `keys` | one of `key`/`keys` | Set of addresses, any of which matches. Takes priority over `key` if both are set. |
| `logLevel` | no | See [Logging](../logs.md#application-log). |

## Behavior

- Recipients are the `To` and `Cc` addresses, plus those of the account's
  [`envelopeHeaders`](../configuration.md#account-fields). Mail received through a mailing list,
  as Bcc or through a catch-all only carries your address in those headers.
- Only the **topmost** occurrence of each envelope header is read: servers add headers on top, so
  the others come from earlier hops or from the sender.
- Learnable: dropping an example message into
  `mom-rules/RECIPIENT_ADDRESS_EQUALS/<ACTION_TYPE>/<key>` teaches a rule using the recipient that
  is one of the account's [`addresses`](../configuration.md#account-fields) (or its SMTP
  `fromAddress`). Fails to learn when none or several of them are among the recipients. An account
  without any address learns from a message with a single recipient only.

## Example

```json
{
  "matcher": { "type": "RECIPIENT_ADDRESS_EQUALS", "key": "shop@example.com" },
  "action": { "type": "MOVE_TO", "key": "Shopping" }
}
```
