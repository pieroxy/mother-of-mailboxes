# Matcher: `AND`

[← back to docs](../README.md)

Composite matcher: matches only if **every** child matcher matches. Evaluation short-circuits
at the first child that doesn't match.

## Config fields

| Field | Required | Description |
|---|---|---|
| `children` | yes | List of nested matcher configs (`type` + whatever fields that type needs). |
| `logLevel` | no | See [Logging](../README.md#logging). Applies only to this `AND` node itself, not to its children — set `logLevel` on each child individually if you need to trace them. |

## Behavior

- With no children, `AND` always matches.
- Not learnable by example.

## Example

Only match a domain when its DKIM signature actually checks out:

```json
{
  "matcher": {
    "type": "AND",
    "children": [
      { "type": "FROM_DOMAIN_EQUALS", "key": "newsletter.example.com" },
      { "type": "DKIM_RESULT_EQUALS", "key": "pass" }
    ]
  },
  "action": { "type": "MOVE_TO", "key": "Newsletters" }
}
```
