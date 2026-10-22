# Matcher: `FROM_ADDRESS_REGEXP`

[← back to docs](../README.md)

Matches when the sender's email address (just `local@domain`, ignoring any display name) matches
the configured [regular expression](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Pattern.html).
The whole address must match, not just part of it.

## Config fields

| Field | Required | Description |
|---|---|---|
| `key` | one of `key`/`keys` | Regular expression to match against, e.g. `".*@(sales\|support)\\.example\\.com"`. |
| `keys` | one of `key`/`keys` | Set of regular expressions, any of which matches. Takes priority over `key` if both are set. |
| `logLevel` | no | See [Logging](../logs.md#application-log). |

## Behavior

- Only matches messages with **exactly one** `From:` address.
- An invalid regular expression fails as soon as the account starts, not on the first message.
- Not learnable by example.

## Example

Catch any address under a couple of subdomains without listing every one individually:

```json
{
  "matcher": { "type": "FROM_ADDRESS_REGEXP", "key": ".*@(sales|support)\\.example\\.com" },
  "action": { "type": "READ" }
}
```
