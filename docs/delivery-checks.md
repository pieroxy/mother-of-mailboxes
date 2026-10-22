# Delivery checks

[← back to docs](README.md)

Checks that an account's **outgoing** mail is set up to be delivered, rather than rejected or sent to
spam. In the web UI: account page → **Check delivery settings** → **Start diagnostics**. Run on demand,
results aren't kept.

- **Domain checked**: the domain of the account's SMTP From address. No SMTP settings: nothing to check.
- **Test message**: with an [SMTP sink](#smtp-sink) configured, a message is sent through the account's
  SMTP server and read back from the sink, to see how the mail really leaves (sending IP, HELO, envelope
  sender, signatures). Without one, checks requiring it are skipped, those it improves are partial (see
  the table).
- **Statuses**: OK, Warning, Error, Info, Skipped (couldn't run). **Partial** means part of a check
  couldn't run — no test message, or port 25 unreachable from where MOM runs (often blocked on home
  connections).

| Check | Test message | What it checks |
|---|---|---|
| SPF | improves it | One valid record, ≤10 DNS lookups, no `ptr`, ending with `-all`/`~all`. With a test message: the real SPF result for the real sending IP and envelope sender, and whether it aligns with the From domain. |
| DKIM | required | Signed; each signature verifies; key published, ≥2048 bits, not revoked or in testing; no SHA-1, no `l=`; a signature by the From domain. |
| DMARC | improves it | One valid record (own or inherited from the organizational domain), policy strength, `pct`. With a test message: whether it passes, through SPF and/or DKIM. |
| DMARC reports | — | `rua=` present (severity depends on the policy), valid `mailto:` destinations able to receive mail, and authorized when outside your domain. |
| Reverse DNS & HELO | required | The sending IP has a PTR resolving back to it, not a residential-looking one; the HELO name is a real host name, consistent with it. |
| MX | — | Your domain can receive replies and bounces: MX records present, not null, pointing to names (not IPs or aliases) with public addresses. |
| DANE | — | TLSA records on your MX hosts, DNSSEC-signed, matching the certificate each MX presents on port 25. Optional. |
| MTA-STS & TLS-RPT | — | MTA-STS record and policy valid, every MX listed in it and presenting a valid certificate on port 25; TLS-RPT record valid. Optional. |
| IP reputation | improves it | The sending IP, and the rest of its /24, against your `IP_CIDR` [reputation lists](reputation-lists.md). Without a test message: the SMTP server's IPs. |
| Domain reputation | improves it | Your From domain — plus envelope and DKIM domains with a test message — against your `DOMAIN` reputation lists. |

What leaves the box during a run:
- DNS lookups for your own domain, MX hosts and sending IP. DANE validates DNSSEC itself; if the
  system resolver doesn't pass DNSSEC data, its lookups go to `1.1.1.1` (shown on the card).
- Connections to your MX hosts on port 25 (DANE, MTA-STS), and an HTTPS fetch of your MTA-STS policy.
- With an SMTP sink: the test message, through your SMTP server.

The test message:
- Goes from the account's From address to `mom-test@<sink domain>`, its subject a random ID.
- One per run, shared by all checks; arrives within seconds usually, given up after 3 minutes.
- Counts toward the account's 50 sends per hour. Your provider may keep a copy in your Sent folder.

## SMTP sink

A receive-only mail server: it accepts the test message, records how it arrived, and hands it back to
MOM. Only that makes the checks see what receivers see.

- **Public instance**: `https://smtp-sink.mom.pieroxy.net` — General settings → Mail Diagnostics →
  **Use the public instance**, then **Test SMTP Sink** and save.
- **Privacy**: whoever runs a sink sees your sending server's IP address and domain, as any recipient of
  your mail would. The message itself only carries a random ID, and is deleted after 5 minutes.
- **Off by default**. Self-hosting a sink is possible, not documented yet.
