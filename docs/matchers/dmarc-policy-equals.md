# Matcher: `DMARC_POLICY_EQUALS`

[← back to docs](../README.md)

Matches the **effective DMARC policy** published by the sender's domain — not whether this
particular message passed or failed. **Case-insensitive.**

## Config fields

| Field | Required | Description |
|---|---|---|
| `key` | one of `key`/`keys` | Policy to match: `none`, `quarantine`, `reject`, `unpublished`, `permerror`, or `temperror`. |
| `keys` | one of `key`/`keys` | Set of values, any of which matches. Takes priority over `key` if both are set. |
| `logLevel` | no | See [Logging](../logs.md#application-log). |

## Use it with `DMARC_RESULT_EQUALS`

[`DMARC_RESULT_EQUALS`](dmarc-result-equals.md) tells whether *this message* passed. The policy
tells how much a `fail` means:

- `reject`: the domain says misaligned mail isn't theirs (banks, payment processors, most large
  services). A `fail` from them is almost certainly spoofed.
- `none`: the domain only monitors, often while still setting things up. A `fail` from them can
  be one of their own sending services not covered yet.

Combine the two with `AND` to act on `fail` only when the domain has a strict policy:

```json
{
  "matcher": {
    "type": "AND",
    "children": [
      { "type": "DMARC_RESULT_EQUALS", "key": "fail" },
      { "type": "DMARC_POLICY_EQUALS", "key": "reject" }
    ]
  },
  "action": { "type": "MOVE_TO_AND_READ", "key": "Spam" }
}
```

A subdomain without its own DMARC record gets its organizational domain's subdomain policy
(`sp=`, or `p=` if there's none).

## Result values

| Value | Meaning |
|---|---|
| `none` | The domain publishes DMARC but its policy is `p=none` (monitor only) — an explicit, active choice. |
| `quarantine` | The domain asks that misaligned mail be quarantined (e.g. spam-folder it). |
| `reject` | The domain asks that misaligned mail be rejected outright. |
| `unpublished` | The domain (and its organizational domain) has **no** DMARC record at all. |
| `permerror` | The published record is malformed, or ambiguous (more than one TXT record found), or `p=`/`sp=` has a value other than `none`/`quarantine`/`reject`. |
| `temperror` | A DNS lookup failed temporarily. |

`unpublished` isn't `none`: no DMARC record at all is the norm for small domains and
individuals, and not a sign of anything wrong by itself.

## Example

```json
{
  "matcher": { "type": "DMARC_POLICY_EQUALS", "key": "unpublished" },
  "action": { "type": "READ" }
}
```
