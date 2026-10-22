# Reputation lists

[← back to docs](README.md)

IP and domain lists, downloaded periodically and checked locally: nothing about a specific
message is ever sent anywhere.

`reputationLists` (top-level, not per-account) declares external IP/domain reputation sources
to download and keep refreshed — never queried live per message, only ever a periodic bulk
download, so no per-message data (sender, IP, subject...) ever leaves the box. Each entry:

```json
{
  "id": "spamhaus-drop",
  "type": "IP_CIDR",
  "url": "https://www.spamhaus.org/drop/drop.txt",
  "refreshHours": 24,
  "score": 1.0
}
```

| Field | Description |
|---|---|
| `id` | Referenced from a matcher's `listIds` (see below). Unique across `reputationLists`. |
| `type` | `IP_CIDR` (one IPv4 or CIDR block per line, e.g. `203.0.113.0/24`) or `DOMAIN` (one domain per line, exact match). Determines which matcher can use the list — `IP_REPUTATION_EQUALS` for `IP_CIDR`, [`FROM_DOMAIN_REPUTATION_EQUALS`](matchers/from-domain-reputation-equals.md) for `DOMAIN`. |
| `url` | `https://...`/`http://...` to download, or `file://...` for a local file you maintain yourself. |
| `refreshHours` | How often to re-download (minimum enforced: 1 hour). |
| `score` | 0 (ok) to 1 (spam): the value attributed to anything found in this list. |

Every list in `reputationLists` is downloaded and kept up to date, whether or not a rule uses it:

- The last downloaded copy is kept on disk and loaded at startup, so lists work from the first
  message, even offline.
- A list is downloaded again once `refreshHours` have passed since the last successful download,
  including across restarts.
- If a download fails (source down, no network), the last copy stays in use. MOM tries again 5
  minutes later, then 10, 20... (never more than `refreshHours` apart) until it succeeds.
- The home page shows each list's age and size. Its **Refresh** button downloads a list right
  away.

In a list file, blank lines are ignored, and `#` or `;` start a comment, on its own line or after
an entry (`1.10.16.0/20 ; SBL256894`). A malformed entry is skipped, not the whole list.

Lists are held in memory. Most are a few MB, but some are much bigger: HaGeZi's NRD7, in the
starter configuration, has ~2.5M entries (~40MB to download). On a small machine, check MOM's
memory use after adding one.

See [Starter configuration](configuration.md#starter-configuration) for the free sources wired up by
default.

See [`IP_REPUTATION_EQUALS`](matchers/ip-reputation-equals.md) and
[`FROM_DOMAIN_REPUTATION_EQUALS`](matchers/from-domain-reputation-equals.md) for how a matcher
references one or more lists (`listIds`) and a threshold (`key`, e.g. `">0.5"`) — when several
referenced lists contain the same value, the worst (highest) score wins.
