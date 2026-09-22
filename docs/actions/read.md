# Action: `READ`

[← back to docs](../README.md)

Marks the message as read (`\Seen`), in place — doesn't move or otherwise touch it.

## Config fields

| Field | Required | Description |
|---|---|---|
| `logLevel` | no | See [Logging](../README.md#logging). No `key` needed. |

## Behavior

- Sets the `\Seen` flag on the message.
- Learnable: dropping an example message into `mom-rules/<MATCHER_TYPE>/READ/<key>` teaches a
  rule that just marks matching mail as read.

## Example

```json
{
  "matcher": { "type": "FROM_ADDRESS_EQUALS", "keys": ["boss@example.com", "hr@example.com"] },
  "action": { "type": "READ" }
}
```
