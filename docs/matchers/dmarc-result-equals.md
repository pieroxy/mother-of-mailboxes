# Matcher: `DMARC_RESULT_EQUALS`

[← back to docs](../README.md)

Matches when a **live-evaluated** DMARC (RFC 7489) result equals the configured value.
**Case-insensitive.**

## Config fields

| Field | Required | Description |
|---|---|---|
| `key` | one of `key`/`keys` | DMARC result to match: `pass`, `fail`, `none`, `permerror`, or `temperror`. |
| `keys` | one of `key`/`keys` | Set of results, any of which matches. Takes priority over `key` if both are set. |
| `logLevel` | no | See [Logging](../README.md#logging). |

## What DMARC checks

DMARC ties [SPF](spf-result-equals.md) and [DKIM](dkim-result-equals.md) to the domain the
recipient sees in `From:`. It passes if either one passed **and** is aligned with that domain:

- **SPF**: the domain SPF checked (`Return-Path`) matches the `From:` domain.
- **DKIM**: the signing domain of a valid signature matches the `From:` domain.

"Matches" means the same organizational domain (`mail.example.co.uk` and `example.co.uk`), or
the exact same domain if the sender's DMARC record asks for strict alignment.

A subdomain without its own DMARC record uses its organizational domain's.

## Result values

| Value | Meaning |
|---|---|
| `pass` | SPF or DKIM passed **and** was aligned with `From:`. |
| `fail` | A DMARC record exists, but neither SPF nor DKIM passed-and-aligned. |
| `none` | No DMARC record is published for the domain (or its organizational domain). |
| `permerror` | The published record is malformed (missing the required `p=` tag), or ambiguous (more than one TXT record found). |
| `temperror` | A DNS lookup failed temporarily. |

This matcher only tells you whether the mail passed or failed — not how strongly the domain
itself vouches for that verdict. See [`DMARC_POLICY_EQUALS`](dmarc-policy-equals.md) for the
domain's published policy (`p=none/quarantine/reject`), which lets you tell a `fail` from a
domain that's serious about DMARC apart from a `fail` from one that's still rolling it out.

## Example

```json
{
  "matcher": { "type": "DMARC_RESULT_EQUALS", "key": "fail", "logLevel": "DEBUG" },
  "action": { "type": "MOVE_TO", "key": "Spam" }
}
```
