# Matcher: `IP_REPUTATION_EQUALS`

[← back to docs](../README.md)

Matches when the connecting IP's reputation score — the worst (highest) score among the
referenced `IP_CIDR` [reputation lists](../README.md#reputation-lists) that contain it — crosses
a threshold.

## Config fields

| Field | Required | Description |
|---|---|---|
| `listIds` | yes | Set of `id`s from `reputationLists` to check against. Must all be of type `IP_CIDR`. |
| `key` | yes | A comparison, not a value to match: an operator (`>`, `>=`, `<`, or `<=`) immediately followed by a number between 0 and 1, e.g. `">0.5"` or `"<=0.1"`. |
| `logLevel` | no | See [Logging](../README.md#logging). |

## How the check works

- The connecting IP is the one [`SPF_RESULT_EQUALS`](spf-result-equals.md#how-the-check-works)
  uses. IPv4 only: IPv6 addresses never match.
- The lists are checked locally, in memory: nothing is sent anywhere per message.
- If the IP is in several lists, the highest `score` is compared to `key`. In none: no match.

Not learnable by example.

## Example

```json
{
  "matcher": { "type": "IP_REPUTATION_EQUALS", "listIds": ["spamhaus-drop"], "key": ">0.5" },
  "action": { "type": "MOVE_TO_AND_READ", "key": "Spam" }
}
```

## Requiring agreement between independent lists

With several `listIds`, one list alone is enough to match. To require two independent lists to
agree (a stronger signal, as in the [starter config](../../config.example.json)), use one matcher
per list, combined with `AND`/`OR`:

```json
[
  {
    "matcher": {
      "type": "AND",
      "children": [
        { "type": "IP_REPUTATION_EQUALS", "listIds": ["spamhaus-drop"], "key": ">0.5" },
        { "type": "IP_REPUTATION_EQUALS", "listIds": ["blocklist-de-mail"], "key": ">0.5" }
      ]
    },
    "action": { "type": "MOVE_TO_AND_READ", "key": "Spam" }
  },
  {
    "matcher": {
      "type": "OR",
      "children": [
        { "type": "IP_REPUTATION_EQUALS", "listIds": ["spamhaus-drop"], "key": ">0.5" },
        { "type": "IP_REPUTATION_EQUALS", "listIds": ["blocklist-de-mail"], "key": ">0.5" }
      ]
    },
    "action": { "type": "MOVE_TO", "key": "Spam" }
  }
]
```

The `AND` rule must come first (see [Rule evaluation order](../README.md#rule-evaluation-order)):
an IP in both lists goes to Spam marked read, an IP in only one of them goes to Spam unread, for
review.
