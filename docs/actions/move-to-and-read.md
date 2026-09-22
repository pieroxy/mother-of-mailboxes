# Action: `MOVE_TO_AND_READ`

[← back to docs](../README.md)

Marks the message as read, **then** moves it. Equivalent to an `AND` of `READ` followed by
`MOVE_TO` with the same key — provided as a shorthand since it's a very common combination.

## Config fields

| Field | Required | Description |
|---|---|---|
| `key` | yes | Target folder name — same as [`MOVE_TO`](move-to.md). |
| `logLevel` | no | See [Logging](../README.md#logging). Applies to both the implicit `READ` and `MOVE_TO` steps. |

## Behavior

- **Order matters, and it's deliberate**: [`MOVE_TO`](move-to.md) copies the message to the
  target folder with whatever flags it has *at the moment of the copy*. Marking it read first
  means the copy that lands in the target folder is already `\Seen`. Doing it the other way
  around would only mark the (about-to-be-deleted) source copy as read, and the copy in the
  target folder would still show as unread.
- Under the hood this isn't a dedicated class: it's built at runtime as an `AND` action whose
  two children (a `READ` config with no key, and a `MOVE_TO` config using this action's own
  `key`/`logLevel`) are constructed on the fly.
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
