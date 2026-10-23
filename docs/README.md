# MOM documentation

MOM (Mother Of Mailboxes) watches one or more IMAP accounts and applies rules to new mail: move
it, mark it read, forward it. Rules are set up in the web UI, taught by example by dropping
messages into special IMAP folders, or written in `config.json`.

- [Quick start](quickstart.md) — download the jar and try it.
- [Running MOM](running.md) — first start, systemd service, upgrading, reverse proxy, backup.
- [Troubleshooting](troubleshooting.md) — an account in error, a rule that didn't match, classifiers, locked out.
- [Using the web UI](web-ui.md) — accounts, rules, stats, the decision log, settings.
- [Rules](rules.md) — matchers and actions, and the order rules are evaluated in.
- [Learning rules by example](learning.md) — teaching rules by dropping messages into `mom-rules/`.
- [Spam classifiers](classifiers.md) — spam detection trained on your own mail.
- [Reputation lists](reputation-lists.md) — IP and domain lists, downloaded periodically.
- [Delivery checks](delivery-checks.md) — is your own outgoing mail set up to be delivered?
- [Running an SMTP sink](smtp-sink.md) — your own server to receive the delivery checks' test message.
- [Configuration](configuration.md) — `config.json` and `credentials.json` reference, and the
  starter configuration.
- [Logs and data files](logs.md) — what MOM writes, and where.

Each [matcher](rules.md#matchers-and-actions) and action has its own page, linked from
[Rules](rules.md).

Digging into the code? [Implementation details](IMPLEMENTATION-DETAILS.md) covers the "why" behind
a few internals.

## Third-party data

- `src/mom-core/src/main/resources/unicode/confusables.txt`, from Unicode's
  [security data](https://www.unicode.org/Public/security/latest/) (UTS #39), © Unicode, Inc.,
  under the [Unicode License v3](https://www.unicode.org/license.txt) (copy alongside it). Used by
  [`LOOKALIKE_TEXT_EQUALS`](matchers/lookalike-text-equals.md).
