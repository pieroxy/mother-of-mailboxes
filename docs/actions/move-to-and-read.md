# Action: `MOVE_TO_AND_READ`

[← back to docs](../README.md)

Marks the message as read, **then** moves it. Equivalent to an `AND` of `READ` followed by
`MOVE_TO` with the same key — provided as a shorthand since it's a very common combination.

## Config fields

| Field | Required | Description |
|---|---|---|
| `key` | yes | Target folder name — same as [`MOVE_TO`](move-to.md). |
| `logLevel` | no | See [Logging](../logs.md#application-log). Applies to both the implicit `READ` and `MOVE_TO` steps. |

## Behavior

- The message arrives in the target folder already read.
- Learnable, same as [`MOVE_TO`](move-to.md): dropping an example message into
  `mom-rules/<MATCHER_TYPE>/MOVE_TO_AND_READ/<key>` teaches a rule that marks matching mail read
  and moves it to the folder named `<key>`.

## Example

```json
{
  "matcher": { "type": "SPF_RESULT_EQUALS", "key": "fail" },
  "action": { "type": "MOVE_TO_AND_READ", "key": "Spam" }
}
```
