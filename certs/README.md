# Lab TLS material

The Console–Sensor link has two modes (`com.mirage.common.LinkSecurity`):

- **No keystore (the default):** plain text, but it never leaves the machine. The Console
  listens on `127.0.0.1` only, and a Sensor refuses a Console on another host. This is the
  `defender` role and the one-laptop demo: Console and Sensor run on the same laptop, so there
  is nothing to set up.
- **Lab keystore:** mutual TLS. Both apps load the *same* keystore, present its certificate and
  trust nothing else, so a stray client can't feed the Console fake events and an impostor
  can't send a Sensor orders. Use this when a Sensor runs on a different machine from its
  Console.

If TLS is asked for but can't be used (no password, wrong password, missing file), the app
**refuses to start**. It never falls back to plain text, and there is no default password.

## 1. Generate the lab keystore (once)

Run this **here** (`certs/`). Choose your own password; keystore files (`*.p12`, `*.jks`) are
gitignored, never commit them.

```bash
keytool -genkeypair -alias mirage-lab -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore lab.p12 -dname "CN=mirage-lab" -validity 365
```

Copy `lab.p12` to every machine that runs the Console or a Sensor (USB stick or similar).

## 2. Set the password, then start with the keystore

The password is read from the `MIRAGE_TLS_PASSWORD` environment variable only. A `-D` flag
would show up in the process list. PowerShell:

```powershell
$env:MIRAGE_TLS_PASSWORD = "your-keystore-password"
java "-Dmirage.keystore=certs/lab.p12" -jar target/mirage.jar console
```

macOS / Linux:

```bash
export MIRAGE_TLS_PASSWORD='your-keystore-password'
java -Dmirage.keystore=certs/lab.p12 -jar target/mirage.jar sensor --console-host 192.168.0.107
```

Look for `Sensor link: mutual TLS, pinned to the lab certificate in certs/lab.p12.` on the
Console and `Console link: mutual TLS, ...` on the Sensor. Each command a Sensor applies is
logged with where it came from, e.g. `[defence] applied BLOCK 10.10.0.5 (from the Console at
192.168.0.107:8443, TLS-verified)`.

The old `-Dmirage.keystore.password`, `-Dmirage.truststore` and `-Dmirage.truststore.password`
flags are no longer read. If one is set, the app stops and says what to use instead.
