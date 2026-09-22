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

## Why "live-verified"

Same reasoning as [`SPF_RESULT_EQUALS`](spf-result-equals.md): MOM never reads or trusts a
pre-existing `Authentication-Results` header for DKIM. It always re-verifies the signature
itself, on the message's raw bytes.

## How the check works

DKIM verification is real cryptographic signature checking (RSA or Ed25519) over
headers and body canonicalized according to precise RFC 6376 rules — unlike SPF, there's no
"just compare strings" shortcut, and a single implementation detail gotten wrong can silently
fail signatures that are actually valid. MOM delegates this to
[`org.apache.james.jdkim`](https://james.apache.org/jdkim/) rather than reimplementing it:

1. The message's raw bytes (headers + body, exactly as received) are read.
2. Every `DKIM-Signature` header on the message is parsed and verified independently: its
   selector (`s=`) and signing domain (`d=`) are used to fetch the public key from
   `<selector>._domainkey.<domain>` (DNS TXT record), and the signature is checked
   cryptographically against the canonicalized headers/body.
3. A message can carry more than one signature (multiple signers). **One valid signature is
   enough** for an overall `pass` — mirroring how a single passing SPF `include` is enough.
4. If no signature is valid, the most informative failure across all of them is reported, in
   this priority order: `fail` > `permerror` > `temperror` > `policy` > `neutral` > `none`.

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
