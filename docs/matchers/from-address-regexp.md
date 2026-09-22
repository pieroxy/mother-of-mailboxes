# Matcher: `FROM_ADDRESS_REGEXP`

[← back to docs](../README.md)

Matches when the sender's email address (just `local@domain`, ignoring any display name) fully
matches the configured regular expression (like [`String.matches`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html#matches(java.lang.String)) — the whole address must match, not just a
part of it).

## Config fields

| Field | Required | Description |
|---|---|---|
| `key` | one of `key`/`keys` | Regular expression to match against, e.g. `".*@(sales\|support)\\.example\\.com"`. |
| `keys` | one of `key`/`keys` | Set of regular expressions, any of which matches. Takes priority over `key` if both are set. |
| `logLevel` | no | See [Logging](../README.md#logging). |

## Behavior

- Only matches messages with **exactly one** `From:` address. A message with zero or multiple
  From addresses never matches (logged at `FINE`).
- Extracts the address part via `InternetAddress.getAddress()`, so display name and any quoting
  are ignored — `"Jean Dupont <jdupont@example.com>"` and `jdupont@example.com` are tested the
  same way.
- Every configured regular expression is compiled once at startup — a typo'd regex fails loudly
  right away (MOM refuses to start) rather than on the first message it's tested against.
- Not learnable: dropping an example message into `mom-rules/` doesn't work for this matcher —
  there's no sensible way to generalize a regular expression from one concrete address the way
  `FROM_ADDRESS_EQUALS` learns a literal one. Write the regex by hand in `config.json`.

## Example

Catch any address under a couple of subdomains without listing every one individually:

```json
{
  "matcher": { "type": "FROM_ADDRESS_REGEXP", "key": ".*@(sales|support)\\.example\\.com" },
  "action": { "type": "READ" }
}
```
