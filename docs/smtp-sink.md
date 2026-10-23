# Running an SMTP sink

[← back to docs](README.md)

[Delivery checks](delivery-checks.md) are only complete with an SMTP sink: a mail server that
receives MOM's test message, records how it arrived, and hands it back. A
[public instance](delivery-checks.md#smtp-sink) is available. Running your own means nobody else
sees your sending server's IP address and domain, and you don't depend on someone else's server.

It's an SMTP server open to the internet, but not an open relay: it never sends anything, refuses
every message but a test message (a UUID as subject, to its own domain), and deletes what it
receives after 5 minutes.

## What you need

- **A machine reachable from the internet on port 25**, inbound. Most home internet connections
  block it, and many hosting providers do too until asked: check before anything else.
- **Java 17.**
- **A domain name** you can add DNS records to, and set the reverse DNS of the machine's IP for
  (usually at your hosting provider).
- **A TLS certificate** for the sink's name, e.g. from Let's Encrypt. It's used for SMTP
  (STARTTLS) and for the HTTPS API.
- **A web server or reverse proxy** for HTTPS (nginx, Caddy...).

## DNS

One name serves everything: the sink's URL, its mail domain, and its certificate. With
`smtp-sink.example.com` on `203.0.113.10`:

```
smtp-sink.example.com.  IN  A      203.0.113.10
smtp-sink.example.com.  IN  MX 10  smtp-sink.example.com.
```

Add an `AAAA` record too if the machine has IPv6. Then set the reverse DNS (PTR) of
`203.0.113.10` to `smtp-sink.example.com`.

## Install as a systemd service

The same layout as for MOM (see [Running MOM](running.md#as-a-systemd-service)): the jar in
`/opt/smtp-sink`, and the config directory, which also holds the received messages, in
`/var/lib/smtp-sink`.

```sh
sudo useradd --system --home /var/lib/smtp-sink --shell /usr/sbin/nologin smtp-sink
sudo mkdir -p /opt/smtp-sink /var/lib/smtp-sink
curl -LO https://github.com/pieroxy/mother-of-mailboxes/releases/latest/download/smtp-sink-core-1.0.0.jar
sudo cp smtp-sink-core-1.0.0.jar /opt/smtp-sink/
sudo ln -sfn smtp-sink-core-1.0.0.jar /opt/smtp-sink/smtp-sink-core.jar
```

Create `/var/lib/smtp-sink/config.json` (see [Configuration](#configuration) for every field):

```json
{
  "hostname": "smtp-sink.example.com",
  "acceptedDomains": ["smtp-sink.example.com"],
  "tls": {
    "certFile": "/var/lib/smtp-sink/tls/fullchain.pem",
    "keyFile": "/var/lib/smtp-sink/tls/privkey.pem"
  }
}
```

```sh
sudo chown -R smtp-sink:smtp-sink /var/lib/smtp-sink
```

Create `/etc/systemd/system/smtp-sink.service`, with your `java` path (`which java`):

```ini
[Unit]
Description=MOM SMTP sink
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=smtp-sink
Group=smtp-sink
ExecStart=/usr/bin/java -jar /opt/smtp-sink/smtp-sink-core.jar /var/lib/smtp-sink
Restart=on-failure
RestartSec=30
# Port 25 without running as root.
AmbientCapabilities=CAP_NET_BIND_SERVICE

NoNewPrivileges=true
ProtectSystem=strict
ProtectHome=true
PrivateTmp=true
ReadWritePaths=/var/lib/smtp-sink

[Install]
WantedBy=multi-user.target
```

Once the certificate is in place (below), start it:

```sh
sudo systemctl daemon-reload
sudo systemctl enable --now smtp-sink
```

The sink has no log file: everything goes to the journal, `journalctl -u smtp-sink -f`. Upgrading
works as for MOM: copy the new jar, point `smtp-sink-core.jar` at it, restart (see
[Upgrading](running.md#upgrading)).

## TLS certificate

The sink reads the certificate and its private key as PEM files: `certFile` with its chain
(Let's Encrypt's `fullchain.pem`), and `keyFile` unencrypted (Let's Encrypt's `privkey.pem`). It
reloads them on its own when they change, so renewals need no restart. The `smtp-sink` user must
be able to read them.

**If your reverse proxy already gets a certificate for that name** and keeps a copy of the files
somewhere, point `certFile` and `keyFile` at them and give the `smtp-sink` user read access.
`ProtectHome=true` hides `/home`: keep the files outside of it.

**With certbot**, its private keys are only readable by root. Copy the files on each renewal with
a deploy hook, `/etc/letsencrypt/renewal-hooks/deploy/smtp-sink.sh`:

```sh
#!/bin/sh
set -e
case " $RENEWED_DOMAINS " in
  *" smtp-sink.example.com "*) ;;
  *) exit 0 ;;
esac
install -d -o smtp-sink -g smtp-sink -m 700 /var/lib/smtp-sink/tls
install -o smtp-sink -g smtp-sink -m 600 "$RENEWED_LINEAGE/fullchain.pem" "$RENEWED_LINEAGE/privkey.pem" /var/lib/smtp-sink/tls/
```

Make it executable, and run it once by hand for the certificate you already have:

```sh
sudo chmod +x /etc/letsencrypt/renewal-hooks/deploy/smtp-sink.sh
sudo RENEWED_DOMAINS=smtp-sink.example.com RENEWED_LINEAGE=/etc/letsencrypt/live/smtp-sink.example.com \
  /etc/letsencrypt/renewal-hooks/deploy/smtp-sink.sh
```

Without `tls`, the sink works but doesn't offer STARTTLS: delivery checks then see your server
sending in clear text, which isn't what your real recipients see.

## HTTPS for the API

MOM fetches the test message back over HTTP, which the sink serves in clear on `127.0.0.1:8025`.
Put it behind your web server, on the sink's name. With nginx:

```nginx
server {
    listen 443 ssl;
    server_name smtp-sink.example.com;
    # ssl_certificate and ssl_certificate_key, as for your other sites

    location / {
        proxy_pass http://127.0.0.1:8025;
    }
}
```

The sink's URL, to give MOM, is then `https://smtp-sink.example.com`.

## Port 25 through a proxy (optional)

By default, the sink listens on port 25 itself. If something else on the machine must own port 25,
or the sink runs elsewhere, forward the TCP connections to it with the PROXY protocol, so it still
sees the sending server's real IP (the point of the test). An HTTP reverse proxy can't do this:
it needs a TCP proxy.

With nginx's `stream` module, on port 25, forwarding to the sink on port 2525:

```nginx
stream {
    server {
        listen 25;
        proxy_pass 127.0.0.1:2525;
        proxy_protocol on;
    }
}
```

And in the sink's `config.json`:

```json
"smtpBindAddress": "127.0.0.1",
"smtpPort": 2525,
"smtpProxyProtocol": true
```

`AmbientCapabilities` is then no longer needed in the service. With HAProxy, use `send-proxy` on
the server line.

## Check it works

1. **The API**: `curl https://smtp-sink.example.com/info` answers with the sink's mail domain.
2. **Receiving mail**: from a machine that can send on port 25 (not a home connection), send a test
   message with [swaks](https://jetmore.org/john/code/swaks/), then fetch it back:
   ```sh
   id=$(uuidgen)
   swaks --to test@smtp-sink.example.com --server smtp-sink.example.com --header "Subject: $id"
   curl https://smtp-sink.example.com/messages/$id
   ```
3. **In MOM**: General settings → Mail Diagnostics → enter the URL → **Test SMTP Sink**, then
   save. This only tests the API: run a delivery check from an account to test the whole path.

## Configuration

`config.json`, in the directory given on the command line. Relative paths are resolved against
that directory.

| Field | Default | Description |
|---|---|---|
| `hostname` | required | The sink's name: in its SMTP greeting and in the `Received` header it adds. Should be the MX record's target. |
| `acceptedDomains` | any | Recipient domains accepted. The first one is the domain MOM sends to. |
| `smtpBindAddress` | `0.0.0.0` | Address to listen on for SMTP. |
| `smtpPort` | `25` | SMTP port. |
| `smtpProxyProtocol` | `false` | Every connection starts with a PROXY protocol v1 header (see [above](#port-25-through-a-proxy-optional)). |
| `smtpTimeoutSeconds` | `60` | Idle time before an SMTP connection is closed. |
| `maxConnections` | `50` | Simultaneous SMTP connections; more are refused. |
| `maxMessageSizeBytes` | `102400` | Bigger messages are refused. |
| `httpBindAddress` | `127.0.0.1` | Address to listen on for the API, in plain HTTP. |
| `httpPort` | `8025` | API port. |
| `storageDir` | `messages` | Where received messages are kept. |
| `retentionSeconds` | `300` | How long a received message is kept. |
| `tls.certFile` | — | The certificate, with its chain, in PEM. Without `tls`, no STARTTLS. |
| `tls.chainFile` | — | The chain, if it's not in `certFile`. |
| `tls.keyFile` | — | The private key: unencrypted PEM, `BEGIN PRIVATE KEY`. |

## Troubleshooting

- **Test SMTP Sink fails in MOM**: check the URL with `curl …/info`. A certificate error or a 502
  points at the web server in front of the sink.
- **Delivery checks wait 3 minutes, then report no test message**: the message never reached the
  sink. Check the MX record, that port 25 is open from the internet, and the sink's journal: a
  refused message is logged with the reason (wrong domain, subject not a UUID, too big).
- **The sink doesn't start**: `journalctl -u smtp-sink` says why — a missing `hostname`, port 25
  without `AmbientCapabilities`, or a certificate file it can't read.
