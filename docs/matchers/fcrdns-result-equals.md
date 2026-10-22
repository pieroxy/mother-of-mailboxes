# Matcher: `FCRDNS_RESULT_EQUALS`

[← back to docs](../README.md)

Matches when a **live-evaluated** FCrDNS (Forward-Confirmed reverse DNS) result equals the
configured value. **Case-insensitive.**

## Config fields

| Field | Required | Description |
|---|---|---|
| `key` | one of `key`/`keys` | Result to match: `pass`, `fail`, `none`, or `temperror`. |
| `keys` | one of `key`/`keys` | Set of results, any of which matches. Takes priority over `key` if both are set. |
| `logLevel` | no | See [Logging](../logs.md#application-log). |

## What this checks

Whether the IP that delivered the message (the one [`SPF_RESULT_EQUALS`](spf-result-equals.md)
uses) has a reverse DNS name that points back to it. It says nothing about the sender's domain.

Properly run mail servers almost always pass. Spam sources (infected home computers, cheap
servers) often don't — but so do some legitimate small servers, and a spammer on a properly set
up server passes. A weak signal: best combined with another one via [`AND`](and.md).

## Result values

| Value | Meaning |
|---|---|
| `pass` | The IP has a PTR record, and at least one of its hostnames resolves back to the same IP. |
| `fail` | The IP has one or more PTR records, but none of them resolve back to it. |
| `none` | The IP has no PTR record at all. |
| `temperror` | A DNS lookup (PTR or the forward confirmation) failed temporarily. |

## Example

```json
{
  "matcher": { "type": "FCRDNS_RESULT_EQUALS", "key": "none", "logLevel": "DEBUG" },
  "action": { "type": "MOVE_TO", "key": "Spam" }
}
```
