# Matcher: `SUBJECT_STARTS_WITH`

[← back to docs](../README.md)

Matches when the message's `Subject:` starts with the configured value. **Case-insensitive.**

## Config fields

| Field | Required | Description |
|---|---|---|
| `key` | one of `key`/`keys` | Subject prefix to match, e.g. `"Your invoice"`. |
| `keys` | one of `key`/`keys` | Set of prefixes, any of which matches. Takes priority over `key` if both are set. |
| `logLevel` | no | See [Logging](../README.md#logging). |

## Behavior

- A message with no `Subject:` header never matches.
- The comparison is a plain prefix check (`subject.startsWith(key)`, case-insensitive) — no
  wildcards, no regex.

## Learning by example — read this before using it

`SUBJECT_STARTS_WITH` is learnable (`mom-rules/SUBJECT_STARTS_WITH/<ACTION_TYPE>/<key>`), but
**naively**: there is no way to automatically know which part of an example's subject is the
prefix you actually want versus content specific to that one message. For example, given
`"Your invoice #12345 is ready"`, should the learned prefix be `"Your invoice"`, or the whole
string?

So for now, dropping an example teaches the rule using the example's **entire subject,
verbatim**, as the key. This is almost never what you want as a long-term rule — it'll only
ever match that exact one subject again. After learning, you need to **hand-edit the learned
rule** to shorten the key to the actual prefix you meant:

1. Drop your example message into `mom-rules/SUBJECT_STARTS_WITH/<ACTION_TYPE>/<key-folder-name>`.
2. Let a cycle run — the rule is learned (with the full subject as its key) and persisted to
   `<dataFolder>/<displayName>-learned-rules.json` (see [Data files](../README.md#data-files)).
3. Open that file, find the rule (`"type": "SUBJECT_STARTS_WITH"`), and edit its `key` (or
   `keys`) down to the prefix you actually want.
4. **Restart MOM.** The in-memory rule list is only reloaded from disk when a *new* example
   gets learned during a cycle — a manual edit to the file doesn't trigger that by itself, so
   without a restart your edit sits on disk unused until the next time something else happens
   to be learned.

## Example

```json
{
  "matcher": { "type": "SUBJECT_STARTS_WITH", "key": "Your invoice" },
  "action": { "type": "READ" }
}
```
