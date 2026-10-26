# Action: `REPLY`

[← back to docs](../README.md)

Replies to the sender with a fixed plain-text message, through the account's
[SMTP settings](../configuration.md#smtp-settings).

## Config fields

| Field | Required | Description |
|---|---|---|
| `body` | yes | The reply's text, plain. |
| `key` | no | The reply's subject. Default: `Re: <original subject>`. |
| `logLevel` | no | See [Logging](../logs.md#application-log). |

## Behavior

- Sends to the message's `Reply-To`, or its `From` without one, from the account's `fromAddress`.
  The reply has `In-Reply-To` and `References`, so mail clients thread it with the original.
- Leaves the original where it is, unread. Combine with `"keepProcessing": true` to let later
  rules file it.
- Never replies to (the action then fails, and the reason is logged at `INFO`):
  - automated messages: an `Auto-Submitted` header other than `no`;
  - bulk and mailing list messages: `Precedence: bulk`, `list` or `junk`, or a `List-Id`,
    `List-Unsubscribe` or `List-Post` header;
  - bounces: an empty `Return-Path`;
  - automated senders: `noreply`, `no-reply`, `do-not-reply`, `mailer-daemon`, `postmaster`,
    `owner-…` and `…-request` addresses;
  - the account's own messages, by `From` or `Reply-To`;
  - messages that have neither the `fromAddress` nor one of the `aliases` of the
    [SMTP settings](../configuration.md#smtp-settings) in `To` or `Cc`: mail received through a
    mailing list or as Bcc. Mail sent to an alias missing from the list gets no reply either.
- Replies carry `Auto-Submitted: auto-replied`, so other auto-responders don't answer them.
- Replies to every message the rule matches: put it after the spam rules, so spam (often with a
  forged sender) never gets one.
- Same hourly cap and single-send guarantee as `FORWARD`: see
  [Sending mail](forward.md#sending-mail). Not learnable.

## Example

```json
{
  "matcher": { "type": "FROM_DOMAIN_EQUALS", "key": "example.org" },
  "action": {
    "type": "REPLY",
    "key": "Out of office",
    "body": "I'm away until Monday 3 November, with no access to my mail."
  },
  "keepProcessing": true
}
```
