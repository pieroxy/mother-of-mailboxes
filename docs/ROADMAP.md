# Roadmap

* Ability to edit complex actions in the UI
* More matchers:
  * Recipient-based: TO_EQUALS/CC_EQUALS/recipient-domain matching
  * Subject: CONTAINS, MATCHES (regex)
  * Generic headers: HEADER_EQUALS/HEADER_CONTAINS(name, value)
  * Body: BODY_CONTAINS, BODY_MATCHES
  * SIZE
  * Attachment: HAS_ATTACHMENT, FILENAME_ENDS_WITH, FILENAME_IS
  * Dated: MESSAGE_AGE, MESSAGE_DATE
  * IMAP_FLAGS
  * Online reputation sources: Spamhaus DBL, ZEN, SpamCop, Barracuda, Mailspike, PSBL, UCEPROTECT level 1 - with optional Spamhaus DQS key: these lists refuse queries through public resolvers (client shared with the delivery checks)
  * Add IPv6 reputation lists
  * Check body links against Spamhaus DBL, SURBL, URIBL
  * Classifier using Maxent (not just Bayes)
* More actions:
  * COPY_TO
  * DELETE
  * SET_FLAG
  * REPLY
* Delivery sanity checks
  * Check against online reputation sources
  * Build a dashboard with DMARC report matcher - volume per receiver, pass rates, unknown senders, readiness for p=reject
    * A matcher to route them, and a parser
  * Scheduled delivery checks with alerts
  * DANE check: test the 1.1.1.1 fallback against a DNSSEC-stripping resolver, make the fallback resolver configurable (or disableable), and track root key rollovers (RFC 5011)
* SMTP sink
  * Web UI
  * Documentation to self-host it
* SMTP front-end mode: sit in front of the MX to filter during the SMTP session (reject, real client IP, no IMAP polling)
* Far-fetched:
  * BIMI, if showing a logo ever stops requiring a paid certificate
