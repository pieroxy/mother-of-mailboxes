# Matcher: `OR`

[← back to docs](../README.md)

Composite matcher: matches if **any** child matcher matches. Evaluation short-circuits at the
first child that matches.

## Config fields

| Field | Required | Description |
|---|---|---|
| `children` | yes | List of nested matcher configs (`type` + whatever fields that type needs). |
| `logLevel` | no | See [Logging](../README.md#logging). Applies only to this `OR` node itself, not to its children — set `logLevel` on each child individually if you need to trace them. |

## Behavior

- With no children, `OR` never matches.
- Not learnable by example.
- If you just need "any of these values", most leaf matchers already support a `keys` field
  (e.g. `FROM_ADDRESS_EQUALS` with `"keys": ["a@x.com", "b@x.com"]`) — reach for `OR` only when
  combining **different matcher types**, or the same type with different `logLevel`s.

## Example

```json
{
  "matcher": {
    "type": "OR",
    "children": [
      { "type": "SPF_RESULT_EQUALS", "key": "fail" },
      { "type": "DKIM_RESULT_EQUALS", "key": "fail" }
    ]
  },
  "action": { "type": "MOVE_TO", "key": "Spam" }
}
```
