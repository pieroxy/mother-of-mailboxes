# Action: `AND`

[← back to docs](../README.md)

Composite action: runs its children **in order**, stopping at the first one that fails.

## Config fields

| Field | Required | Description |
|---|---|---|
| `children` | yes | List of nested action configs (`type` + whatever fields that type needs), run in list order. |
| `logLevel` | no | See [Logging](../logs.md#application-log). Applies only to this `AND` node itself, not to its children — set `logLevel` on each child individually if you need to trace them. |

## Behavior

- With no children, `AND` does nothing and succeeds.
- For read-then-move, [`MOVE_TO_AND_READ`](move-to-and-read.md) is shorter.
- Not learnable by example.

## Example

```json
{
  "matcher": { "type": "SPF_RESULT_EQUALS", "key": "fail" },
  "action": {
    "type": "AND",
    "children": [
      { "type": "READ" },
      { "type": "MOVE_TO", "key": "Spam" }
    ]
  }
}
```
