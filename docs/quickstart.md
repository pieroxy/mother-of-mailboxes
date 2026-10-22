# Quick start

Try MOM locally, in a throwaway directory. Once you're happy with it, see
[Running MOM](running.md) to install it for good, as a service.

## Try it out

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
[learning rules by example](learning.md), and processes new mail as it
arrives. Mail already in the INBOX is left alone. Watch `./data/logs/log.txt` (or the console) to confirm it's picking up mail and
matching rules. Ctrl-C stops it.

You can also write `config.json` and `credentials.json` by hand instead, starting from
[`config.example.json`](../config.example.json) and
[`credentials.example.json`](../credentials.example.json) — see the
[configuration reference](configuration.md#configuration-file).

Once you're satisfied it's working, see [Running MOM](running.md) to install it as a systemd
service, and move what you've set up here into it.
