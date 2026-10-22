# Matcher: `NOT`

[← back to docs](../README.md)

Composite matcher: matches if its single child matcher does **not**.

## Config fields

| Field | Required | Description |
|---|---|---|
| `children` | yes | Exactly one nested matcher config (`type` + whatever fields that type needs). |
| `logLevel` | no | See [Logging](../logs.md#application-log). Applies only to this `NOT` node itself, not to its child. |

## Behavior

- Requires **exactly one** child: anything else is refused.
- Not learnable by example.
- For a single matcher, listing the values you want in `keys` is often clearer: `SPF_RESULT_EQUALS`
  with `"keys": ["fail", "softfail"]` rather than `NOT` of `pass`.

## Example

Move to Spam anything that isn't from a trusted domain and fails DKIM:

```json
{
  "matcher": {
    "type": "AND",
    "children": [
      { "type": "NOT", "children": [{ "type": "FROM_DOMAIN_EQUALS", "key": "trusted.example.com" }] },
      { "type": "DKIM_RESULT_EQUALS", "key": "fail" }
    ]
  },
  "action": { "type": "MOVE_TO", "key": "Spam" }
}
```
