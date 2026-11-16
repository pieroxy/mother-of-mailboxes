# Matcher: `LIST_ID_EQUALS`

[← back to docs](../README.md)

Matches when the mailing list identifier of the `List-Id` header equals the configured value.
**Case-insensitive.** Useful for sorting the messages of a mailing list.

## Config fields

| Field | Required | Description |
|---|---|---|
| `key` | one of `key`/`keys` | List identifier to match, e.g. `"dev.lists.example.org"`. |
| `keys` | one of `key`/`keys` | Set of list identifiers, any of which matches. Takes priority over `key` if both are set. |
| `logLevel` | no | See [Logging](../logs.md#application-log). |

## Behavior

- The identifier is the part between angle brackets: in `List-Id: "Dev discussions"
  <dev.lists.example.org>`, it is `dev.lists.example.org`, so renaming the list doesn't break the
  rule. Without brackets, the whole value is used.
- Only the **topmost** `List-Id` is read: it is the list that delivered the message.
- The sender sets this header: fine for sorting, not to be trusted.
- Learnable: dropping an example message into `mom-rules/LIST_ID_EQUALS/<ACTION_TYPE>/<key>`
  teaches a rule using its list identifier. Fails to learn without a `List-Id`.

## Example

```json
{
  "matcher": { "type": "LIST_ID_EQUALS", "key": "dev.lists.example.org" },
  "action": { "type": "MOVE_TO", "key": "Lists/Dev" }
}
```
