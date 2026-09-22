# Matcher: `SPF_RESULT_EQUALS`

[← back to docs](../README.md)

Matches when a **live-verified** SPF (RFC 7208) result equals the configured value.
**Case-insensitive.**

## Config fields

| Field | Required | Description |
|---|---|---|
| `key` | one of `key`/`keys` | SPF result to match: `pass`, `fail`, `softfail`, `neutral`, `none`, `permerror`, or `temperror`. |
| `keys` | one of `key`/`keys` | Set of results, any of which matches. Takes priority over `key` if both are set. |
| `logLevel` | no | See below — particularly useful here, set to `"DEBUG"` to see the full DNS trace. |

## Why "live-verified"

Most mail servers add an `Authentication-Results` (or `Received-SPF`) header once they've
checked SPF themselves. MOM **never reads or trusts those headers** — it always redoes the
check itself, for two reasons:

1. Your own receiving server may not check SPF at all (the reason this matcher exists).
2. Even when such a header is present, nothing stops a sender from forging one — it's just a
   regular header. Unless the receiving infrastructure is known to strip untrusted incoming
   copies of it before delivery, a forged `Authentication-Results: ...; spf=pass` would
   otherwise be taken at face value.

## How the check works

1. **Connecting IP**: read from the topmost (most recent) `Received:` header — the one added by
   your own IMAP server when it accepted the message over SMTP, which reflects the real TCP
   connection and can't be forged by the sender the way older `Received:` headers can.
2. **Sender domain**: the domain of `Return-Path` (the actual envelope sender, written by the
   final delivering MTA from the SMTP `MAIL FROM`), falling back to the domain of `From:` if no
   `Return-Path` is present.
3. The domain's `v=spf1` TXT record is fetched and evaluated against the connecting IP:
   `ip4`, `ip6`, `a`, `mx`, `include` (recursive) and `all` mechanisms are supported, along with
   the `redirect` modifier. A DNS lookup budget of 10 (RFC 7208 §4.6.4) prevents runaway
   recursion through `include` chains.

If the connecting IP or sender domain can't be determined (e.g. no `Received:` header at all),
the matcher simply doesn't match — it never throws.

## Known limitations

- **Macros** (`%{...}`) in a mechanism's value aren't supported; a mechanism using one is
  skipped rather than failing the whole record (macros are almost always used only in `exp=`,
  which MOM never reads, so this rarely matters in practice).
- **`ptr`** is deliberately never matched, as recommended by the RFC — it requires an
  unreliable reverse-DNS lookup.
- The evaluation trusts the topmost `Received:` header as having been added by your own,
  trusted, receiving infrastructure.

## Result values

| Value | Meaning |
|---|---|
| `pass` | The IP is explicitly authorized. |
| `fail` | The IP is explicitly **not** authorized (`-all` or an explicit `-` mechanism matched). |
| `softfail` | The IP is probably not authorized, but the domain's policy says not to hard-reject (`~all`). |
| `neutral` | The domain makes no assertion either way. |
| `none` | The domain publishes no SPF record at all. |
| `permerror` | The published SPF record is malformed, or too complex to evaluate (lookup budget exceeded). |
| `temperror` | A DNS lookup failed temporarily (timeout, SERVFAIL). |

`softfail` is common even for large, legitimate providers who prefer not to hard-reject mail
that doesn't match their published sources (see the [example config](../README.md#example-configjson),
which routes `fail` to Spam pre-marked read and `softfail` to Spam left unread for review).

## Example

```json
{
  "matcher": { "type": "SPF_RESULT_EQUALS", "key": "fail", "logLevel": "DEBUG" },
  "action": { "type": "MOVE_TO_AND_READ", "key": "Spam" }
}
```
