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
  	by mail.mom.pieroxy.net (MOM SMTP sink) with ESMTPS (TLSv1.3:TLS_AES_256_GCM_SHA384)
  	for <test@mail.mom.pieroxy.net>; Fri, 16 Oct 2026 21:52:32 +0200
  ```
- **HTTP:** `GET /messages/<uuid>` returns the stored message (`200`, `message/rfc822`), or `404`
  if there is none: never received, or older than `retentionSeconds` (default 5 minutes).

## Build & run

```
mvn clean package
java -jar target/mom-smtp-sink-1.0.0.jar /path/to/config-dir
```

Same convention as MOM: the argument is a directory holding `config.json` (start from
`config.example.json`). Relative paths in it are resolved against that directory. Logs go to
stderr.

## Deployment notes

- **Port 25 must reach the sink with the client's real IP.** Either bind it directly, or put it
  behind a TCP proxy that speaks PROXY protocol v1 (nginx `stream { ... proxy_protocol on; }`,
  HAProxy `send-proxy`) and set `smtpProxyProtocol` to `true`. An HTTP reverse proxy can't carry
  SMTP.
- **HTTP** is plain and bound to `127.0.0.1` by default: put your HTTPS reverse proxy in front.
- **TLS:** PEM files, `certFile` holding the certificate (plus its chain, e.g. certbot's
  `fullchain.pem`; otherwise add `chainFile`) and `keyFile` an unencrypted PKCS#8 key
  (`BEGIN PRIVATE KEY`, which certbot writes). They are reloaded automatically when they change
  on disk. The user running the sink must be able to read them: certbot's `privkey.pem` is
  root-only by default.
- **DNS:** an MX record for the accepted domain pointing to `hostname`, which should also be the
  certificate's name and have matching forward and reverse DNS.
- Binding port 25 as non-root: `setcap 'cap_net_bind_service=+ep'` on the java binary, or
  `AmbientCapabilities=CAP_NET_BIND_SERVICE` in a systemd unit.
