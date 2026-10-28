# Troubleshooting

[← back to docs](README.md)

Where to look first:

- **The account's page** in the web UI: its status, and its last error.
- **The [decision log](logs.md#decision-log)**: for each message, every rule it went through and
  why it matched or not. Off by default: enable it in the account's General settings.
- **MOM's log**: the Logs icon in the web UI's top bar, or `<dataFolder>/logs/log.txt`. Under
  systemd, `journalctl -u mom` shows what happened before the log file was set up.

## Can't reach the web UI

- **From another machine**: MOM only listens on `127.0.0.1` at first. Use an SSH tunnel
  (`ssh -L 8080:127.0.0.1:8080 <machine>`), or a [reverse proxy](running.md#behind-a-reverse-proxy).
- **"Too many failed logins"**: after 5 failed logins within 5 minutes, an address is refused
  until the oldest failure is 5 minutes old, even with the right password. Wait, then try again.
  Behind a reverse proxy without `trustedProxies`, every visitor shares the proxy's address, and
  so the lock-out.
- **"Too many requests" on every page**: the address was banned for 15 minutes, after 4 requests
  for pages that don't exist within 10 seconds, without being logged in. MOM's log names the
  address and the last path asked for. Typing URLs by hand can trigger it: wait for the ban to end.
- **Lost the password**: see [First start](running.md#first-start).

## An account is in error

The account's page shows the last error. To tell connection problems apart, open the account's
settings and test the IMAP connection: it says whether the host can't be found, the connection is
refused or times out, the TLS handshake fails, or the login is refused.

- **The server or the network was down**: nothing to do. Each failed cycle waits longer before the
  next one (up to 30 minutes), but MOM checks every minute whether the server is back, and runs a
  cycle as soon as it is. **Run now**, on the account's page, starts one right away.
- **Timeouts on a slow server**: raise the account's read timeout (account settings → General).
- **Login refused**: update the password in the account's IMAP settings. MOM only supports IMAPS
  (implicit TLS, usually port 993).

## A rule didn't do what I expected

Find the message in the decision log, and open its details:

- **Another rule matched first**: rules run in order and the first match wins (see
  [Rule evaluation order](rules.md#rule-evaluation-order)). Move the rule up, or use
  `keepProcessing` on the earlier one.
- **No rule matched**: check each condition. For SPF, DKIM and DMARC, set `"logLevel": "DEBUG"` on
  the matcher to log the whole check (DNS records, signatures) in MOM's log.
- **A learned rule matched instead of a manual one, or the other way around**: learned rules run
  after the manual ones, unless the "Learned rules run here" entry places them elsewhere.
- **The rule matched, but nothing happened**: the action failed (a folder that can't be created,
  an SMTP error...). The log says why. A failed action still stops evaluation.
- **The message isn't in the decision log**: the decision log was off when it arrived, or the
  message was already in the INBOX when the account was added (see below). To run the rules on
  it, drop it into `mom-rules/ToProcess` (see
  [Manually reprocessing a message](learning.md#manually-reprocessing-a-message)).

## Learning by example didn't work

The example ends up in `mom-rules/Done`, unread. The decision log (trigger: Learning) says what
was learned, or why nothing could be:

- **The folder isn't seen**: the path must be exactly `mom-rules/<MATCHER_TYPE>/<ACTION_TYPE>/<key>`,
  or a [shortcut](learning.md#learning-shortcuts) folder. Only four matchers can be learned:
  `FROM_EQUALS`, `FROM_ADDRESS_EQUALS`, `FROM_DOMAIN_EQUALS` and `SUBJECT_STARTS_WITH`.
- **`SUBJECT_STARTS_WITH` learned the whole subject**: it always does. Edit the learned rule
  (account settings → Learned Rules) to keep only the prefix.
- **A learned rule edited by hand in its file isn't applied**: restart MOM. Edits made from the web
  UI apply right away.
- **Too many folders in the mail client** (Apple Mail shows them all): use
  [shortcuts](learning.md#learning-shortcuts) and set `discoveryTreeDisabled` on the account.

## Spam classifiers

- **They never match**: they need enough examples first — 50 spam and 50 legitimate messages for
  the subject and header classifiers, 150 of each for the body one. The account's page shows
  where it stands. Models are retrained once a day, or right away with **Run now**.
- **Not enough legitimate examples**: mail left in `INBOX` doesn't count. File or archive the
  mail you keep into folders.
- **Too many false positives**: raise the threshold (`>0.99` rather than `>0.9`), or use two
  rules: a strict threshold that moves the mail, and a lower one that leaves it unread for review
  (see [`SUBJECT_CLASSIFIER_EQUALS`](matchers/subject-classifier-equals.md)). Look in the
  classifier's folder regularly at first, and move legitimate mail back out: it's learned from.
- **They get worse over time**: their own folder must be in `classifierExcludedFolders`, or they
  learn from their own verdicts (see [Excluding a folder](classifiers.md#excluding-a-folder-from-the-corpus)).

## Reputation lists

- **A list is in warning or error on the home page**: its last downloads failed. MOM keeps using
  the last copy, and retries on its own. The refresh icon tries again right away; MOM's log says
  why a download failed.
- **MOM uses a lot of memory**: lists are held in memory, and some are big (HaGeZi's NRD7 has
  ~2.5M entries). Remove the biggest ones, or give Java more memory (`java -Xmx…`).

## Some mail wasn't processed at all

MOM processes mail arriving in the INBOX, including what arrived while it was stopped: it picks
up where it left off. It starts from the newest message, leaving older mail alone, when:

- the account was just added: mail already in the INBOX is never processed;
- its `<dataFolder>/<account>.json` file, which records where it left off, was lost (see
  [Backup](running.md#backup-and-moving-to-another-machine));
- the mailbox was recreated on the server.

To run the rules on such mail, drop it into `mom-rules/ToProcess`.
