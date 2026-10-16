# Matcher: `LOOKALIKE_TEXT_EQUALS`

[← back to docs](../README.md)

Matches when the subject or the sender's display name contains a word written to look like
something it isn't, using the tricks described by Unicode's security report
([UTS #39](https://www.unicode.org/reports/tr39/)). **Case-insensitive.** No network access: it
only reads the headers.

## Config fields

| Field | Required | Description |
|---|---|---|
| `key` | one of `key`/`keys` | Kind to match: `mixed_script`, `whole_script`, or `fancy_letters`. |
| `keys` | one of `key`/`keys` | Set of kinds, any of which matches. Takes priority over `key` if both are set. |
| `logLevel` | no | See [Logging](../README.md#logging). |

## Kinds

| Value | Meaning | Example |
|---|---|---|
| `mixed_script` | A word mixing Latin with Cyrillic or Greek letters (or other unusual mixes). | `Pаypal` — the `а` is Cyrillic |
| `whole_script` | A word entirely in Cyrillic or Greek letters that each look like a Latin one, among Latin words. | `Рау your invoice` — `Рау` is all Cyrillic, reads "Pay" |
| `fancy_letters` | A word with two or more mathematical or circled forms of Latin letters. | `𝐅𝐫𝐞𝐞`, `ⓕⓡⓔⓔ` |

Words are split on spaces, punctuation, hyphens and apostrophes. Digits, punctuation and accents
belong to every alphabet, so they never make a word "mixed".

What's not flagged, to avoid false positives:

- **East Asian text**: Japanese mixes Han, Hiragana, Katakana and Latin in one word, Chinese Han
  and Latin, Korean Hangul and Latin — all normal.
- **Latin with another alphabet than Cyrillic or Greek** (e.g. Arabic, Thai): their letters don't
  pass for Latin ones.
- **Genuinely Russian or Greek text**: `whole_script` only applies when no other word of the text
  is plainly Cyrillic or Greek, and there's at least one Latin word to blend into.
- **Full-width letters** (`ＦＲＥＥ`), ordinary in Chinese and Japanese text, and symbols like `™`,
  `²` or `①`.

The match detail names the word and why, e.g. `subject: Pаypal (Latin+Cyrillic)` or
`display name: Рау → Pay (Cyrillic)`, as shown in the decision log.

The Cyrillic/Greek look-alike table comes from Unicode's `confusables.txt`, bundled with MOM
(see [Third-party data](../README.md#third-party-data)).

## Example

```json
{
  "matcher": { "type": "LOOKALIKE_TEXT_EQUALS", "keys": ["mixed_script", "whole_script", "fancy_letters"] },
  "action": { "type": "MOVE_TO", "key": "Spam", "spam": true }
}
```
