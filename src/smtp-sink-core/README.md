# MOM SMTP sink

A receive-only SMTP server for MOM's mail server diagnostics. A MOM instance sends a test
message whose Subject is a fresh UUID to this server through its own outgoing SMTP setup, then
fetches it back over HTTP to see how it arrived: sending IP, HELO name, TLS, SPF/DKIM/DMARC
material.

## Contract

- **SMTP:** any message to an `acceptedDomains` recipient whose Subject is a UUID is accepted
  (up to `maxMessageSizeBytes`). Anything else is refused during the SMTP session, never bounced.
  A `Return-Path` and a `Received` header are added on top, recording the client IP, the
  HELO/EHLO name and the TLS protocol and cipher:
  ```
  Return-Path: <sender@example.org>
  Received: from mx.example.org ([203.0.113.7])
  	by smtp-sink.mom.pieroxy.net (MOM SMTP sink) with ESMTPS (TLSv1.3:TLS_AES_256_GCM_SHA384)
  	for <test@smtp-sink.mom.pieroxy.net>; Fri, 16 Oct 2026 21:52:32 +0200
  ```
- **HTTP:** `GET /info` returns what a MOM instance needs to use the sink, so its settings only
  need the sink's URL:
  ```json
  {"mailDomain":"smtp-sink.mom.pieroxy.net","maxMessageSizeBytes":102400,"retentionSeconds":300}
  ```
  `mailDomain` is the first of `acceptedDomains` (or `hostname` if that list is empty): MOM sends
  its test messages to any address at that domain.
- **HTTP:** `GET /messages/<uuid>` returns the stored message (`200`, `message/rfc822`), or `404`
  if there is none: never received, or older than `retentionSeconds` (default 5 minutes).

## Build

```
mvn clean package
```

Running it, DNS, TLS, configuration: see [Running an SMTP sink](../../docs/smtp-sink.md).
