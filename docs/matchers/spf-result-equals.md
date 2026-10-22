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

## How the check works

MOM checks SPF itself: `Authentication-Results` and `Received-SPF` headers already on the message
are ignored, since a sender can forge them.

- **Connecting IP**: from the topmost `Received:` header, the one added by your own mail server.
- **Sender domain**: the domain of `Return-Path`, or of `From:` if there's no `Return-Path`.

If either can't be determined, the matcher doesn't match.

Not supported: macros (`%{...}`, almost only used in `exp=`, which MOM doesn't read) and the `ptr`
mechanism, which the RFC advises against.

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
that doesn't match their published sources (see the [example config](../configuration.md#example-configjson),
which routes `fail` to Spam pre-marked read and `softfail` to Spam left unread for review).

## Example

```json
{
  "matcher": { "type": "SPF_RESULT_EQUALS", "key": "fail", "logLevel": "DEBUG" },
  "action": { "type": "MOVE_TO_AND_READ", "key": "Spam" }
}
```
