# Implementation details

[← back to docs](README.md)

Internals that don't belong in [the main docs](README.md) (which document how to configure and
use MOM) but are worth writing down somewhere rather than only living in code comments — the
"why" behind a design choice that isn't obvious from reading the code alone.

## Classifier corpus deduplication by Message-ID

`ClassifierCorpusStore` tracks, per folder, a UID cursor — a message already recorded from a
given folder is never recorded again *from that same folder*. Moving a message to another folder
is a different story: IMAP gives it a new UID in the destination folder, invisible to that
cursor, so it gets captured again.

This is actually useful, not a bug, for the common case of correcting a misclassification: a
message auto-captured `SPAM` from the Spam folder (scanned every cycle), then moved out by hand
because it wasn't spam, gets re-captured `HAM` by the next daily full scan. Rather than the two
contradictory captures both counting and canceling each other out in training, `ClassifierCorpusStore.readAll()`
deduplicates by the message's `Message-ID` header, keeping only the example with the most recent
`fetchDate` — i.e. the latest-known verdict, which reflects wherever the user ended up filing the
message. Examples without a `Message-ID` (rare, malformed mail) are never deduplicated against
each other, since there's no reliable key to match them on.

## Reputation list pairing (AND/OR) rationale

The starter config's IP and domain reputation pairs — Spamhaus DROP + FireHOL's
`blocklist_de_mail`; HaGeZi's TIF mini + the Blocklist Project's phishing list — are combined via
`AND`/`OR` rather than referenced together in one matcher's `listIds`. The two sources in each
pair use different detection methodologies, so agreement between them is a meaningfully stronger
signal than either alone; two lists that substantially overlap (e.g. one that's itself an
aggregate including the other) wouldn't give the same benefit. `disposable-email-domains` and
HaGeZi's `NRD7` are used standalone since there's no obvious independent second source for
either.

`reputationLists` downloads every configured list unconditionally rather than lazily activating
only the ones a matcher references — simpler to reason about ("configured means downloaded"),
and a list is warm and ready the moment a rule is wired to it, without waiting for the next
refresh cycle.

## Domain reputation lists use a flat HashSet, not a trie

`DomainReputationList` stores its entries in a plain `HashSet<String>`. HaGeZi's `NRD7` list is
~2.5 million entries (~40MB) — much larger than every other list in the starter config (a few
hundred thousand entries, a few MB) — and was benchmarked against a compressed trie
(`StringTree`) as a possible memory optimization. Even after several rounds of optimization (path
compression on reversed domain strings — domains share suffixes, not prefixes — and sorted-array
child dispatch instead of a `HashMap` per node), the trie still used noticeably more memory than
the flat set on real data: Java's compact strings already make a flat `HashSet` quite efficient,
and the per-node object overhead of a trie never fully paid for itself on this dataset.
`StringTree` is kept in the codebase (tested, benchmarked) but not wired into production.

`IpReputationList`, by contrast, *does* use a custom structure — `IpTrie`, a binary trie over IP
address bits — instead of a linear scan. There the win was CPU (a lookup is bounded to 32
comparisons regardless of list size, vs. scanning every entry), not memory: 37x-225x faster on
real reputation lists in benchmarks, for a negligible memory cost, since IP lists are orders of
magnitude smaller than `NRD7`.
