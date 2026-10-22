# Action: `MOVE_TO`

[← back to docs](../README.md)

Moves the message to another IMAP folder.

## Config fields

| Field | Required | Description |
|---|---|---|
| `key` | yes | Target folder, `"/"`-separated for a nested one, e.g. `"Admin/Backups"` or `"[Gmail]/Spam"`. |
| `logLevel` | no | See [Logging](../README.md#logging). |

## Behavior

- `"/"` in `key` always means "nested folder", regardless of the IMAP server's actual hierarchy
  delimiter.
- The target folder — and every missing intermediate one for a nested `key` — is created before
  the message is moved into it.
- The message keeps its flags: if it was read, it stays read.
- Learnable: dropping an example message into `mom-rules/<MATCHER_TYPE>/MOVE_TO/<key>` teaches a
  rule that moves matching mail to the folder named `<key>`.

## Example

```json
{
  "matcher": { "type": "FROM_DOMAIN_EQUALS", "key": "newsletter.example.com" },
  "action": { "type": "MOVE_TO", "key": "Newsletters" }
}
```
