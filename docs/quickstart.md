# Quick start

Two steps: try MOM locally in a throwaway directory, then turn it into a proper Linux service
once you're happy with the config.

## 1. Try it out

You need Java 17 and network access to your IMAP server (and to the public DNS for SPF/DKIM/
DMARC/FCrDNS checks — see [What is it?](../README.md#what-is-it)).

```sh
mkdir mom && cd mom
curl -LO https://github.com/pieroxy/mother-of-mailboxes/releases/latest/download/mom-core-1.0.0.jar
java -jar mom-core-1.0.0.jar .
```

The argument is the directory holding `config.json` and `credentials.json`. On the first start,
neither exists: MOM creates both, and prints the web UI's address and a temporary password on the
console (and only there). Open that address, log in as `admin`, choose a new password, and the
setup wizard walks you through the web server, the data folder and the reputation lists, then
through adding your first mail account.

The web UI only listens on `127.0.0.1` at first. On a remote machine, either use an SSH tunnel
(`ssh -L 8080:127.0.0.1:8080 <machine>`, with the port MOM printed), or set `webServer.address` in
`config.json` before restarting MOM.

MOM starts one thread per account, connects, creates the `mom-rules/` folder skeleton used for
[learning rules by example](README.md#learning-rules-by-example), and processes new mail as it
arrives. Watch `./data/logs/log.txt` (or the console) to confirm it's picking up mail and
matching rules. Ctrl-C stops it.

You can also write `config.json` and `credentials.json` by hand instead, starting from
[`config.example.json`](../config.example.json) and
[`credentials.example.json`](../credentials.example.json) — see the
[configuration reference](README.md#configuration-file).

Once you're satisfied it's working, move on to running it as a service.

## 2. Run it as a systemd service on Linux

This sets MOM up under a dedicated, unprivileged system user, with the FHS-style path the example
config already assumes (`/var/lib/mom`, which also holds the logs — see
[Logging](README.md#logging)).

Create the user and directory:

```sh
sudo useradd --system --home /opt/mom --shell /usr/sbin/nologin mom
sudo mkdir -p /opt/mom /var/lib/mom
```

Put the jar and your finished `config.json`/`credentials.json` (from step 1, with `dataFolder`
set to `/var/lib/mom`) into `/opt/mom`:

```sh
sudo cp mom-core-1.0.0.jar config.json credentials.json /opt/mom/
sudo chown -R mom:mom /opt/mom /var/lib/mom
sudo chmod 600 /opt/mom/credentials.json
```

Find your `java` binary (`which java`), then create `/etc/systemd/system/mom.service`:

```ini
[Unit]
Description=MOM - IMAP mail filter daemon
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=mom
Group=mom
ExecStart=/usr/bin/java -jar /opt/mom/mom-core-1.0.0.jar /opt/mom
Restart=on-failure
RestartSec=30

NoNewPrivileges=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/var/lib/mom

[Install]
WantedBy=multi-user.target
```

(Adjust the `ExecStart` java path to match `which java`. The last argument, `/opt/mom`, is the
directory MOM reads `config.json` from — it happens to be the same directory as the jar here,
but doesn't have to be.)

Enable and start it:

```sh
sudo systemctl daemon-reload
sudo systemctl enable --now mom
sudo systemctl status mom
```

Two places to look for logs: `/var/lib/mom/logs/log.txt` (MOM's own rotating log, per
`keepLogFiles` in the config — see [Logging](README.md#logging)) for the day-to-day activity,
and `journalctl -u mom -f` for service-level output (startup, crashes, anything printed before
the log file is set up).

To pick up a config change, restart the service. The same applies if you hand-edit
`learned-rules.json` — see [`SUBJECT_STARTS_WITH`](matchers/subject-starts-with.md) for why:

```sh
sudo systemctl restart mom
```
