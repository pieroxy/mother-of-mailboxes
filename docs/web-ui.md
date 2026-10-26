# Using the web UI

[← back to docs](README.md)

The web UI is where MOM is set up and watched day to day. It's served by MOM itself, on the
address and port printed at first start (see the [Quick start](quickstart.md)).

The top bar has, on the left, a refresh icon reloading the current page's data, and on the right:
**Home**, **Logs** (MOM's own logs), **General settings** and **Profile**.

## First start

On the first start, MOM prints a temporary password on the console (see
[First start](running.md#first-start), also to reset a lost password). Log in as `admin`, choose a
new password, and a setup wizard walks you through:

1. **Web server**: the port and address the web UI listens on.
2. **Storage**: the data folder, where MOM keeps its state and logs.
3. **Reputation lists**: which of the recommended [lists](reputation-lists.md) to use.

It then opens the account creation wizard.

## Home page

One card per account: its status, messages processed and matched, rules, time until the next
cycle, and how far its [spam classifiers](classifiers.md) are from being trained. Click a card to
open the account's page.

The dashed **Add account** card at the end opens the account creation wizard.

Below, the [reputation lists](reputation-lists.md): each one's age, number of entries and size.
Its refresh icon downloads it again right away.

## Adding an account

The wizard asks for:

1. **Name**: shown everywhere, and used to name the account's files.
2. **IMAP connection**: server, port, username and password, with a button to test them.
3. **Sending mail** (optional): the [SMTP settings](configuration.md#smtp-settings), needed to
   forward, redirect or reply. Also tested before going further.
4. **Spam folder**: where spam goes, created if missing. Everything outside it (and `INBOX`) is
   treated as legitimate mail by the classifiers.
5. **Spam handling**: whether to create the recommended spam rules (see the
   [starter configuration](configuration.md#starter-configuration)), and the folder for the
   subject classifier's verdicts.
6. **Schedule**: how often the account runs, and its connection timeouts.
7. **Review**: everything above, and the rules that will be created.

Nothing is saved before **Create account**.

## Account page

- **Status**: OK, running, paused, or in error with the last error message. **Pause Account** stops
  it without deleting anything.
- **Cycles**: when the last one ran and the next one will. **Run now** starts one right away.
- **Activity**: messages processed and matched since the account started.
- **Spam classifiers**: examples collected, and which classifiers are trained.
- **Connection**: the IMAP and SMTP servers.

From there, and from the icons at the top of each account page: **Settings**, **Stats**, **Logs**
(the decision log), and **Check delivery settings** (see [Delivery checks](delivery-checks.md)).

## Account settings

- **General**, **IMAP settings**, **SMTP settings**: the account's configuration. The gear icon
  edits a section; IMAP and SMTP have a button to test the connection.
- **Rules**: in evaluation order (see [Rules](rules.md)). Each one can be moved up or down,
  edited, or deleted; **Add rule** adds one at the end. The "Learned rules run here" entry sets
  where [learned rules](learning.md) are evaluated.
- **Shortcuts**: the [learning shortcuts](learning.md#learning-shortcuts).
- **Learned Rules**: the rules learned by example, which can be edited or deleted.

Changes are marked **New** or **Edited** until **Save Changes**, which applies them all at once
and restarts the account. **Discard Changes** drops them. Pausing, resuming and deleting the
account (in the **Danger Zone**) apply right away.

In the rule editor, **When** is the matcher, as a tree: `AND`, `OR` and `NOT` groups can be
nested at will. **Then** is a single action; a rule whose action is a group (`AND`/`OR` of
actions) is shown read-only there, and can only be changed in `config.json`.

## Stats

Messages processed and matched per day, and the matchers that matched the most, over the last 7
days or any range. Clicking a matcher opens the decision log, filtered on it.

## Decision log

Every message processed by the account, newest first: where it ended up, and every rule it went
through. It's off by default, since it records each message's subject and sender: enable it with
the account's decision log retention (General settings of the account, or the **Enable** button
on the page).

Filters: dates, trigger (arrived in the inbox, reprocessed, or an example to learn from),
outcome, rules that failed, and a search on subject, sender or Message-ID.

## General settings

- **Server**: the data folder, how long logs are kept, and the [trusted proxies](configuration.md#top-level-fields)
  when MOM sits behind a reverse proxy.
- **Web Server**: port, address and secure cookie; port and address changes apply right away.
  **Disable** turns the web UI off for good, until it's re-enabled in `config.json`.
- **Web Login**: the username and password to log into the web UI.
- **Reputation Lists**: add, change or delete [lists](reputation-lists.md).
- **Mail Diagnostics**: the SMTP sink used by [delivery checks](delivery-checks.md).

Like account settings, changes are saved with **Save Changes**.

## Logs

MOM's application log (filter by level, search) and the web server's access log (filter by HTTP
status), one day at a time. See [Logs and data files](logs.md).

## Profile

Change the web UI password, or log out.

The open sessions are listed with when they started, the address of their last request (the
login's, if different, on hover), how many API calls they made, how long ago the last one was,
and the browser's User-Agent. Any session but the current one can be revoked, one by one or all
at once. Sessions live in memory: restarting MOM logs everyone out.

After 5 failed logins within 5 minutes, an address is refused for a few minutes, even with the
right password. An address asking for 4 pages that don't exist within 10 seconds, without being
logged in, is banned from the web UI for 15 minutes: that's how scanners probing for known paths
behave.
