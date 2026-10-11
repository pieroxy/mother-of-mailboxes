# Matcher: `FROM_VALIDITY_EQUALS`

[← back to docs](../README.md)

Matches when the structural validity of the message's `From:` header equals the configured
value. **Case-insensitive.** No network access: it only reads the header.

## Config fields

| Field | Required | Description |
|---|---|---|
| `key` | one of `key`/`keys` | Validity to match: `valid`, `missing`, `multiple`, or `invalid`. |
| `keys` | one of `key`/`keys` | Set of validities, any of which matches. Takes priority over `key` if both are set. |
| `logLevel` | no | See [Logging](../README.md#logging). |

## Validity values

| Value | Meaning |
|---|---|
| `valid` | Exactly one `From:` header, holding exactly one syntactically valid address. |
| `missing` | No `From:` header, or only blank ones. A `Sender:` header doesn't make up for it. |
| `multiple` | Several `From:` headers, or one header listing several addresses. |
| `invalid` | A single `From:` header from which no valid address can be extracted (e.g. `root`, `John Doe`, `a@@example.com`, `undisclosed-recipients:;`). |

RFC 5322 requires exactly one `From:` header. Several `From:` headers are forbidden outright, and
a known spoofing trick: DMARC and the mail client may not read the same one. Several addresses
in one header are technically allowed (with a `Sender:` header), but no mainstream client
produces them. And DMARC can't evaluate a message without a single usable `From:` domain, so
none of these cases is covered by the DMARC rules.

Internationalized addresses (`josé@exämple.com`, RFC 6531) are `valid`; non-ASCII characters
before the `@` must be letters, digits or combining marks.

False positives come from home-made senders: scripts, cron jobs, NAS or printers sending mail
without a `From:` header or with a bare `root`. Hence `MOVE_TO` (left unread) rather than
`MOVE_TO_AND_READ` in the example below.

## Example

```json
{
  "matcher": { "type": "FROM_VALIDITY_EQUALS", "keys": ["missing", "multiple", "invalid"] },
  "action": { "type": "MOVE_TO", "key": "Spam" }
}
```
