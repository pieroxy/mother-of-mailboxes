# Action: `REDIRECT`

[← back to docs](../README.md)

Re-sends the message unchanged to another address, through the account's
[SMTP settings](../README.md#smtp-settings).

## Config fields

| Field | Required | Description |
|---|---|---|
| `key` | yes | Recipient: a single email address. |
| `logLevel` | no | See [Logging](../README.md#logging). |

## Behavior

- Sends the original message byte for byte, with `Resent-From`, `Resent-To`, `Resent-Date` and
  `Resent-Message-ID` headers added. Bounces go to the account's `fromAddress`.
- The recipient sees the original sender. The original's DKIM signature stays valid, so DMARC
  usually passes on the recipient's side; it fails for unsigned mail from a domain with a strict
  DMARC policy.
- Some providers refuse to send, or rewrite, a message whose `From` isn't the authenticated
  account (Outlook refuses, Gmail replaces it with your own address). Use [`FORWARD`](forward.md)
  with those.
- Leaves the original where it is, unread.
- Same loop protection, hourly cap and single-send guarantee as `FORWARD`: see
  [Sending mail](forward.md#sending-mail). Not learnable.

## Example

```json
{
  "matcher": { "type": "FROM_ADDRESS_EQUALS", "key": "billing@example.org" },
  "action": { "type": "REDIRECT", "key": "invoices@example.net" }
}
```
