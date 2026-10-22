# Action: `FORWARD`

[← back to docs](../README.md)

Forwards the message to another address, as an attachment, through the account's
[SMTP settings](../configuration.md#smtp-settings).

## Config fields

| Field | Required | Description |
|---|---|---|
| `key` | yes | Recipient: a single email address. |
| `logLevel` | no | See [Logging](../logs.md#application-log). |

## Behavior

- Sends a new message from the account's `fromAddress`, subject `Fwd: <original subject>`: a short
  note naming the rule, then the original message attached unchanged (`message/rfc822`).
- Leaves the original where it is, unread. Combine with `"keepProcessing": true` to let later
  rules move it, or use it inside an [`AND`](and.md).
- Recipients see the account as the sender, so SPF/DKIM/DMARC checks pass on their side. To have
  the original sender show instead, see [`REDIRECT`](redirect.md).
- See [Sending mail](#sending-mail) below for what both actions share.

## Sending mail

- The account needs [SMTP settings](../configuration.md#smtp-settings): without, the rule is refused
  when saved, and the account doesn't start.
- Each forwarded message carries an `X-MOM-Forwarded-By: <fromAddress>` header, one per hop. A
  message already carrying the account's own address isn't sent again, which breaks forwarding
  loops, including between several accounts.
- At most 50 messages per account in any rolling hour: beyond that, sending fails with a warning
  in the log, so a rule matching a sudden flood can't get the SMTP account suspended.
- A message is never sent twice: MOM records its progress right after each send.
- Not learnable.

## Example

```json
{
  "matcher": { "type": "FROM_DOMAIN_EQUALS", "key": "accountant.example.com" },
  "action": { "type": "FORWARD", "key": "partner@example.net" },
  "keepProcessing": true
}
```
