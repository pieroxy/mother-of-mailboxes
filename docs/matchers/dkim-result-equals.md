# Matcher: `DKIM_RESULT_EQUALS`

[← back to docs](../README.md)

Matches when a **live-verified** DKIM (RFC 6376) result equals the configured value.
**Case-insensitive.**

## Config fields

| Field | Required | Description |
|---|---|---|
| `key` | one of `key`/`keys` | DKIM result to match: `pass`, `fail`, `none`, `policy`, `neutral`, `temperror`, or `permerror`. |
| `keys` | one of `key`/`keys` | Set of results, any of which matches. Takes priority over `key` if both are set. |
| `logLevel` | no | See below — particularly useful here, set to `"DEBUG"` to see the per-signature trace. |

## How the check works

MOM verifies the signatures itself, like [`SPF_RESULT_EQUALS`](spf-result-equals.md): headers
reporting a result already on the message are ignored.

Every `DKIM-Signature` on the message is checked. **One valid signature is enough** for `pass`.
Otherwise the most significant failure is reported, in this order: `fail`, `permerror`,
`temperror`, `policy`, `neutral`, `none`.

## Result values

| Value | Meaning |
|---|---|
| `pass` | At least one signature verified successfully. |
| `fail` | A signature is present and well-formed, but cryptographically invalid (e.g. the body or a signed header was altered after signing). |
| `none` | The message carries no `DKIM-Signature` header at all. |
| `permerror` | A signature is malformed, or its public key couldn't be found in DNS. |
| `temperror` | A DNS lookup for the public key failed temporarily. |
| `policy` / `neutral` | Rarely produced; see RFC 8601 §2.7.1 if you need the exact semantics. |

## Example

```json
{
  "matcher": { "type": "DKIM_RESULT_EQUALS", "key": "fail", "logLevel": "DEBUG" },
  "action": { "type": "MOVE_TO", "key": "Spam" }
}
```

Combined with a domain check, to only trust a newsletter sender when its DKIM signature for
that exact domain checks out:

```json
{
  "matcher": {
    "type": "AND",
    "children": [
      { "type": "FROM_DOMAIN_EQUALS", "key": "newsletter.example.com" },
      { "type": "DKIM_RESULT_EQUALS", "key": "pass" }
    ]
  },
  "action": { "type": "MOVE_TO", "key": "Newsletters" }
}
```
