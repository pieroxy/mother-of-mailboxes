# Action: `OR`

[← back to docs](../README.md)

Composite action: runs its children **in order**, stopping at the first one that succeeds — a
fallback chain.

## Config fields

| Field | Required | Description |
|---|---|---|
| `children` | yes | List of nested action configs (`type` + whatever fields that type needs), tried in list order. |
| `logLevel` | no | See [Logging](../README.md#logging). Applies only to this `OR` node itself, not to its children — set `logLevel` on each child individually if you need to trace them. |

## Behavior

- With no children, `OR` does nothing and fails.
- Not learnable by example.

## Example

Try moving to a specific folder, falling back to a generic one if that somehow fails:

```json
{
  "action": {
    "type": "OR",
    "children": [
      { "type": "MOVE_TO", "key": "Newsletters/TechCrunch" },
      { "type": "MOVE_TO", "key": "Newsletters" }
    ]
  }
}
```
