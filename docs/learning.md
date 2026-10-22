# Learning rules by example

[← back to docs](README.md)

Instead of writing a rule by hand, you can teach it by dropping example messages into a
specific IMAP folder path:

```
mom-rules/<MATCHER_TYPE>/<ACTION_TYPE>/<key>
```

For example, `mom-rules/FROM_DOMAIN_EQUALS/MOVE_TO/Spam`: every message dropped in that folder
teaches the rule "if the sender's domain equals this message's sender domain, move to Spam".
MOM automatically creates the `<MATCHER_TYPE>/<ACTION_TYPE>` skeleton for every learnable
matcher/action combination each cycle — you only need to create the final `<key>` folder
yourself (its name becomes the action's key, e.g. the target folder for `MOVE_TO`).

Only "learnable" matcher/action types support this (everything except the composite `AND`/`OR`/
`NOT` types, which are reserved for rules you write yourself). Each cycle, for every example message found:

1. The matcher's key is extracted from the example (e.g. its sender's domain).
2. The rule is persisted to `<dataFolder>/<displayName>-learned-rules.json` — a separate file
   from `config.json`, which learning never modifies. If a rule with the same matcher type and
   action already exists, the new key is merged into it (`keys`) rather than creating a
   duplicate rule.
3. The action actually runs on the example message itself.
4. If the example is still present afterward (the action didn't move or delete it), it's moved
   to `mom-rules/Done` — so it isn't re-learned/re-run every cycle. It's marked **unread** on
   the way in, regardless of its state before: a clear indicator in the mail client that
   something there still needs to be manually sorted.

Learned rules come **after** manual rules in evaluation order unless a `LEARNED_RULES` entry
places them elsewhere (see [Rule evaluation order](rules.md#rule-evaluation-order)).

## Learning shortcuts

The discovery tree above creates a folder pair for **every** learnable combination, while most
accounts only use a handful. `learningShortcuts` (per account) gives a specific (matcher type,
action) pair its own flat folder directly under `mom-rules/` instead:

```json
{
  "learningShortcuts": [
    {
      "name": "MoveSameDomainToSpam",
      "matcher": { "type": "FROM_DOMAIN_EQUALS" },
      "action": { "type": "MOVE_TO_AND_READ", "key": "Spam" }
    }
  ]
}
```

Dropping a message into `mom-rules/MoveSameDomainToSpam` teaches exactly the same rule as
`mom-rules/FROM_DOMAIN_EQUALS/MOVE_TO_AND_READ/Spam` would. Shortcuts and the discovery tree can
be used side by side.

- The `action` is fully set in the config, `key` included.
- The `matcher` only takes a `type`: its key comes from each example, so a `key` or `keys` there
  is refused.
- A shortcut's name must be unique, and can't be `Done` or a matcher type name.

Most mail clients let you subscribe to just the shortcut folders. Some (notably Apple Mail, on
iOS and macOS) show every folder anyway: for those, set `discoveryTreeDisabled` on the account
so the discovery tree isn't created at all.

## Manually reprocessing a message

To re-run the full rule catalog against a message that's already in your mailbox (for example,
after adding a rule that should have caught it), drop it into `mom-rules/ToProcess`. Every
cycle, MOM treats every message found there exactly as if it had just arrived: the first
matching rule applies normally. If the message is still present afterward — no rule matched, or
the matching rule's action didn't relocate it — it's moved to `mom-rules/Done` (the same folder
used by the learning system above, marked unread the same way) for manual review.
