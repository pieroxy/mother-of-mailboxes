# Quick start

Two steps: try MOM locally in a throwaway directory, then turn it into a proper Linux service
once you're happy with the config.

## 1. Try it out

You need Java 17 and network access to your IMAP server (and to the public DNS for SPF/DKIM/
DMARC/FCrDNS checks — see [What is it?](../README.md#what-is-it)).

```sh
mkdir mom && cd mom
curl -LO https://github.com/pieroxy/mother-of-mailboxes/releases/latest/download/mom-core-1.0.0.jar
curl -LO https://raw.githubusercontent.com/pieroxy/mother-of-mailboxes/main/config.example.json
curl -LO https://raw.githubusercontent.com/pieroxy/mother-of-mailboxes/main/credentials.example.json
mv config.example.json config.json
mv credentials.example.json credentials.json
```

Edit `config.json` and `credentials.json`:

- In `credentials.json`, fill in `username`/`password` for the `personal` entry (or rename it —
  just keep it matching the `credentials` key used in `config.json`).
- In `config.json`, fill in `host` (and `port`/`displayName` if needed) under `configurations`.
- For this first try, point `dataFolder` at a plain local path instead of `/var/lib/mom` in the
  example — you don't have write access there yet, and you don't want to run as root just to
  test:

  ```json
  "dataFolder": "./data",
  ```

See the [configuration reference](README.md#configuration-file) for what every field does —
the example file is a reasonable starting point (see
[Starter configuration](../README.md#starter-configuration)), not something to use as-is.

Then run it, passing the **directory containing `config.json` and `credentials.json`** (here, the
current directory):

```sh
java -jar mom-core-1.0.0.jar .
```

MOM starts one thread per account, connects, creates the `mom-rules/` folder skeleton used for
[learning rules by example](README.md#learning-rules-by-example), and begins polling every
`runEvery` seconds. Watch `./data/logs/log.txt` (or the console) to confirm it's picking up mail
and matching rules. Ctrl-C stops it.

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
switched back to `/var/lib/mom` as in the example) into `/opt/mom`:

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
