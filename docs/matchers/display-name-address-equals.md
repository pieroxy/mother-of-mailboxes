# Matcher: `DISPLAY_NAME_ADDRESS_EQUALS`

[← back to docs](../README.md)

Matches when an email address shown in the `From:` display name relates to the actual sender
address as configured, e.g. `"service@paypal.com" <x@evil.ru>`. **Case-insensitive.** No
network access: it only reads the header.

DMARC only checks the actual address, and many mail clients show only the display name — a
common phishing pattern that passes SPF, DKIM and DMARC for the attacker's own domain.

## Config fields

| Field | Required | Description |
|---|---|---|
| `key` | one of `key`/`keys` | Value to match: `none`, `same`, `same_domain`, or `other_domain`. |
| `keys` | one of `key`/`keys` | Set of values, any of which matches. Takes priority over `key` if both are set. |
| `logLevel` | no | See [Logging](../README.md#logging). |

## Values

| Value | Meaning | Example |
|---|---|---|
| `none` | No address in the display name, or the `From:` header isn't [`valid`](from-validity-equals.md). | `John Doe <john@example.com>` |
| `same` | Only the actual address. | `"john@example.com" <john@example.com>` |
| `same_domain` | Another address on the same organizational domain. | `"support@example.com" <noreply@mail.example.com>` |
| `other_domain` | An address on another organizational domain. | `"service@paypal.com" <x@evil.ru>` |

With several addresses in the display name, the worst one wins. Domains are compared by
organizational domain (Public Suffix List, as for DMARC). Look-alike characters that Unicode
NFKC normalization folds, such as a fullwidth `＠`, are detected.

`same_domain` is mostly harmless (help desks, ticketing tools). False positives for
`other_domain` come from mailing lists or forwarding services that rewrite `From:` and keep
the original address in the display name. Hence `MOVE_TO` (left unread) in the example below.

## Example

```json
{
  "matcher": { "type": "DISPLAY_NAME_ADDRESS_EQUALS", "key": "other_domain" },
  "action": { "type": "MOVE_TO", "key": "Spam" }
}
```
