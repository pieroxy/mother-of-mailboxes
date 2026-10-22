# Running MOM

[← back to docs](README.md)

Installing MOM for good, and looking after it. To try it first, see the [Quick start](quickstart.md).
When something goes wrong, see [Troubleshooting](troubleshooting.md).

## Command line

MOM requires Java 17. It's a single jar, run with the path to its **config directory**:

```sh
java -jar mom-core.jar /path/to/config-dir
```

The config directory holds `config.json` and `credentials.json`, which MOM creates on first start
and updates whenever settings are saved from the web UI: MOM must be able to write there. Its
other files go to the data folder (`dataFolder` in `config.json`), by default `data/` inside the
config directory.

## First start

With an empty config directory, MOM creates both files and prints, on the console only, the web
UI's address and a temporary password:

- Run by hand, it's in the terminal.
- Run as a systemd service, it's in the journal: `journalctl -u mom`.

The web UI only listens on `127.0.0.1` at first. On a remote machine, reach it through an SSH
tunnel: `ssh -L 8080:127.0.0.1:8080 <machine>`, with the port MOM printed. Then log in as
`admin`, choose a new password, and follow the setup wizard (see [Using the web UI](web-ui.md#first-start)).

**Lost the password?** Set a temporary one by hand:

1. Stop MOM.
2. In `credentials.json`, in the web login's entry (`webui` unless you changed it): delete
   `passwordHash`, and add `"password": "something-temporary"` and `"temporary": true`.
3. Start MOM. It replaces the plain password with a hashed one on startup, and asks for a new
   password right after login.

`passwordHash` must be gone: as long as it's there, the plain `password` is ignored.

## As a systemd service

MOM runs as a dedicated, unprivileged user. The jar goes in `/opt/mom`; the config directory, which
MOM writes to, is `/var/lib/mom`.

```sh
sudo useradd --system --home /var/lib/mom --shell /usr/sbin/nologin mom
sudo mkdir -p /opt/mom /var/lib/mom
sudo cp mom-core-1.0.0.jar /opt/mom/
sudo ln -sfn mom-core-1.0.0.jar /opt/mom/mom-core.jar
sudo chown -R mom:mom /var/lib/mom
```

`mom-core.jar` is a link to the current version, so upgrading never touches the service (see
[Upgrading](#upgrading)).

If you tried MOM first (see the [Quick start](quickstart.md)), copy that directory's
`config.json`, `credentials.json` and `data/` into `/var/lib/mom`, then
`sudo chown -R mom:mom /var/lib/mom` and `sudo chmod 600 /var/lib/mom/credentials.json`. Otherwise,
MOM starts fresh: see [First start](#first-start).

Create `/etc/systemd/system/mom.service`, with your `java` path (`which java`):

```ini
[Unit]
Description=MOM - IMAP mail filter daemon
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=mom
Group=mom
ExecStart=/usr/bin/java -jar /opt/mom/mom-core.jar /var/lib/mom
Restart=on-failure
RestartSec=30

NoNewPrivileges=true
ProtectSystem=strict
ProtectHome=true
PrivateTmp=true
ReadWritePaths=/var/lib/mom

[Install]
WantedBy=multi-user.target
```

Enable and start it:

```sh
sudo systemctl daemon-reload
sudo systemctl enable --now mom
sudo systemctl status mom
```

Logs: the web UI's Logs page, or `/var/lib/mom/data/logs/log.txt` (see
[Logs and data files](logs.md)). `journalctl -u mom` shows what happens before the log file is
set up, such as a startup failure.

Settings changed in the web UI apply on their own (the few that need a restart say so). After
editing `config.json`, `credentials.json` or a `learned-rules.json` file by hand, restart MOM:
`sudo systemctl restart mom`.

## Upgrading

Download the new jar next to the old one, point the link at it, and restart:

```sh
sudo cp mom-core-1.1.0.jar /opt/mom/
sudo ln -sfn mom-core-1.1.0.jar /opt/mom/mom-core.jar
sudo systemctl restart mom
```

To go back, point the link at the previous jar and restart. Old jars can be deleted once you're
happy with the new one.

## Behind a reverse proxy

To reach the web UI over HTTPS, put it behind your web server:

1. Keep MOM listening on `127.0.0.1` (General settings → Web Server → Address).
2. Give it a host name of its own (`mom.example.com`): the web UI can't be served under a path
   (`example.com/mom/`).
3. Have the proxy send the client's address in `X-Forwarded-For`, and list the proxy's address in
   `trustedProxies` (General settings → Server → Trusted proxies). Without it, every request seems
   to come from the proxy: the [access log](logs.md#access-log) is useless, and the login
   lock-out after failed attempts applies to everyone at once.
4. Turn on the secure cookie (General settings → Web Server), so the session cookie is only sent
   over HTTPS. Logging in over plain HTTP then no longer works.

With nginx:

```nginx
server {
    listen 443 ssl;
    server_name mom.example.com;
    # ssl_certificate and ssl_certificate_key, as for your other sites

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

```json
"trustedProxies": ["127.0.0.1"]
```

The proxy must pass cookies through, and never keep any of its own: a proxy storing the cookies
MOM sends would hand your session to every visitor.

## Backup and moving to another machine

Everything is in the config directory (`/var/lib/mom` above):

| What | Lose it and… |
|---|---|
| `config.json`, `credentials.json` | Accounts, rules and logins are gone. |
| `data/<account>-learned-rules.json` | Rules learned by example are gone. |
| `data/<account>.json` | The account starts again from the newest message: mail that arrived since the backup isn't processed. |
| `data/classifier-corpus/` | The classifiers start over, and need weeks of mail to be trained again. |
| `data/reputation/` | Lists are downloaded again on start. Not worth backing up. |
| `data/logs/` | Stats and the decision log history are gone. Optional. |
| `data/webapp-ui/` | Nothing: recreated on every start. |

`credentials.json` holds your IMAP passwords in plain text: keep backups as private as the file.

To move MOM to another machine, stop it, copy the config directory over, and start it there.
