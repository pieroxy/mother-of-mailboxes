# Matcher: `FROM_DOMAIN_REPUTATION_EQUALS`

[← back to docs](../README.md)

Like [`IP_REPUTATION_EQUALS`](ip-reputation-equals.md), but on the domain of the `From:` header
instead of the connecting IP, against `DOMAIN` [reputation lists](../reputation-lists.md).

## Config fields

| Field | Required | Description |
|---|---|---|
| `listIds` | yes | Set of `id`s from `reputationLists` to check against. Must all be of type `DOMAIN`. |
| `key` | yes | A comparison: an operator (`>`, `>=`, `<`, or `<=`) followed by a number between 0 and 1, e.g. `">0.5"`. |
| `logLevel` | no | See [Logging](../logs.md#application-log). |

## How the check works

- The message must have exactly one `From:` address.
- The domain must be in the list exactly (case-insensitive): an entry for `example.com` doesn't
  cover `mail.example.com`.
- If the domain is in several lists, the highest `score` is compared to `key`.

Like [`IP_REPUTATION_EQUALS`](ip-reputation-equals.md), the lists are checked locally. Not
learnable by example.

## Example

```json
{
  "matcher": { "type": "FROM_DOMAIN_REPUTATION_EQUALS", "listIds": ["known-spam-domains"], "key": ">0.5" },
  "action": { "type": "MOVE_TO", "key": "Spam" }
}
```

## Requiring agreement between independent lists

Same pattern as [`IP_REPUTATION_EQUALS`](ip-reputation-equals.md#requiring-agreement-between-independent-lists):
one matcher per list, combined with `AND`/`OR`. The [starter config](../../config.example.json)
does this with [HaGeZi's TIF mini](https://github.com/hagezi/dns-blocklists) and the
[Blocklist Project](https://github.com/blocklistproject/Lists)'s phishing list.

## Not every domain list means "malicious"

The starter config also uses two lists that only mean "unusual", not "malicious", so they send
mail to Spam unread, for review:

- [disposable-email-domains](https://github.com/disposable-email-domains/disposable-email-domains)
  — throwaway-mail providers, anonymous and easily re-created.
- [HaGeZi's NRD7](https://github.com/hagezi/nrd) — domains registered in the last 7 days.
  **Much bigger than the other lists** (~2.5M entries): see
  [Reputation lists](../reputation-lists.md) about memory.
