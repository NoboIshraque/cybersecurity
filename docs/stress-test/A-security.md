# Stress test A — Security and safety

Branch `feature/live-shells` @ `22f951e`. Tester: agent A (security/safety angle; correctness and
concurrency are covered separately in the B checklist).

## Scope / method

- Read the safety boundary (`ScopeGuard`, `LabConfig`), every outbound path (`specter.*`,
  `ResponsePolicy`, `ResponseController`, `CounterOffensive`), the Sensor-Console wire protocol
  (`ConsoleServer`, `SensorClient`, `JsonCodec`, TLS), the decoys, the bait watcher, the ledger and
  signer, the H2 DAOs, and the Swing text sinks.
- Reproduced findings with small harnesses compiled against `target/classes`, and by running the
  Console and Sensor headless on ports 19443/19081/19021/19023 from a scratch working directory.
- All traffic stayed on 127.0.0.1. ScopeGuard "bypasses" are proven by showing the guard *accepts*
  a string and that Java would resolve it differently (using `-Djdk.net.hosts.file`, so no DNS
  query ever left the machine) — nothing was ever sent to an outside address.
- VERIFIED = reproduced (how is stated). SUSPECTED = from reading the code.

## 1. ScopeGuard and the lab range



## 1. Sensor ↔ Console protocol

- [ ] **A-01 · MEDIUM · SUSPECTED** — No line-length limit and an unbounded thread pool on the Console server
  - Where: `ConsoleServer.java:67` (`Executors.newCachedThreadPool()`), `:118-123` (`BufferedReader.readLine()` with no cap); `sensor/SensorClient.java:60` (same on the Sensor side).
  - Problem: one connection that never sends a newline makes `readLine` buffer without limit (memory exhaustion), and every connection gets its own thread with no upper bound, so a burst of connections exhausts threads. Either stops the Console during a demo.
  - Fix: read with a bounded reader (reject lines over e.g. 64 KiB and close the connection), use a fixed-size pool, cap concurrent connections, and set a socket read timeout tied to the heartbeat interval.

- [ ] **A-02 · MEDIUM · SUSPECTED** — TLS is optional, server-only, and defaults to the password `changeit`
  - Where: `ConsoleMain.java:143-158`, `SensorMain.java:90-104`, `TlsFactories.java`.
  - Problem: with no `-Dmirage.keystore`, the link is plain TCP, which is the default in every script and in the two-laptop guide. With TLS, only the server is authenticated. A keystore that fails to load silently falls back to plain sockets. Keystore and truststore passwords default to `changeit`.
  - Fix: make the two-laptop scripts use TLS by default; fail closed (refuse to start) when a configured keystore cannot load, instead of falling back to plaintext; drop the `changeit` default and read the password from an env var or prompt; document generating the lab CA in the setup guide.

- [ ] **A-03 · MEDIUM · SUSPECTED** — A Sensor obeys commands from whatever host it connected to
  - Where: `sensor/SensorClient.java:50-66`, `SensorMain.java:30-45`.
  - Problem: without TLS the Sensor cannot tell its Console from an impostor on `console.host:console.port` (for example after the real Console is down, or with a spoofed address on the LAN). An impostor could push BLOCK/TARPIT/UNBLOCK to the decoys, e.g. UNBLOCK an attacker the operator just blocked.
  - Fix: covered by mandatory TLS with a pinned lab. Also log every applied command with its origin, and consider signing commands with the Console's key.

## 2. Decoys and the bait watcher

- [ ] **A-04 · MEDIUM · SUSPECTED** — Twelve slow or tarpitted connections stall a decoy for everyone
  - Where: `sensor/Decoy.java:29,35` (fixed pool of 12), `:104-118` (tarpit loops until the peer closes); `FakeHttpLoginDecoy.java:46`, `FakeFtpDecoy.java:31`, `FakeTelnetDecoy.java:29-31` (`readLine` with no socket timeout).
  - Problem: a worker is held for as long as the peer keeps the socket open, both for an idle client that never sends a line and for a tarpitted client (by design the tarpit never lets go). Twelve such connections fill the pool, so new connections queue unserved and their events are not reported until a worker frees up. A tarpit meant to slow one attacker ends up freezing the decoy.
  - Fix: set `setSoTimeout` on every accepted socket (e.g. 10–15 s for interaction); cap tarpit hold time and connections per source; run tarpits on a separate small pool or with non-blocking I/O so they cannot starve normal service; report the CONNECT event before queuing.

- [ ] **A-05 · LOW · SUSPECTED** — Bait-file events depend on the watcher only; no integrity baseline
  - Where: `sensor/BaitFileWatcher.java:32-60`.
  - Problem: `WatchService` can drop events (OVERFLOW) and misses changes made while the Sensor is down. There is no hash baseline, so a file replaced while the Sensor was off is never flagged. The bait directory is also relative to the working directory.
  - Fix: record a SHA-256 baseline of each bait file at start and re-check it on a timer and on OVERFLOW; report missing or changed files; make the bait directory explicit in `LabConfig`.

## 3. Ledger and persistence

- [ ] **A-06 · MEDIUM · SUSPECTED** — The ledger signing key is new on every start and never stored, so stored signatures cannot be verified later
  - Where: `console/KeyPairSigner.java:24-33`, `ConsoleMain.java:61`, `console/AlertLedger.java:36-56`.
  - Problem: each Console run generates a fresh ECDSA key pair in memory, and the public key is not persisted or published. Alerts written to H2 by an earlier run carry signatures nobody can check, and the hash chain is unkeyed SHA-256, so someone who can edit the H2 file can rewrite the chain consistently. The "tamper-evident" claim holds only within one run.
  - Fix: persist the key pair in a password-protected PKCS12 keystore (or at least export the public key with a key ID stored in each row); verify the stored chain at start-up; add a `verify` for rows loaded from H2.

- [ ] **A-07 · LOW · SUSPECTED** — The H2 database has no password and lives in the working directory
  - Where: `console/db/Database.java:17`, `ConsoleMain.java:63` (`jdbc:h2:file:./data/mirage`).
  - Problem: anyone with local file access can open or alter the alert and event history; combined with A-06, an edited history goes undetected. (Inserts use `PreparedStatement`; no SQL injection was found in the DAOs.)
  - Fix: set a DB user and password (from config or env), keep the DB in a per-user data directory, and rely on the A-06 verification to detect edits.


## 4. Attacker-side counter-watch

- [ ] **A-08 · LOW · SUSPECTED** — CounterWatch spawns a thread per connection with no cap
  - Where: `specter/CounterWatch.java:28` (`newCachedThreadPool`), `observe()`.
  - Problem: the listener is reachable by anyone on the LAN; each connection costs a thread for up to the 400 ms drain, with no limit on concurrent connections or log volume, so a burst can flood the shell and the thread table on Laptop B.
  - Fix: a fixed-size pool plus a per-source connection cap and rate-limited logging.

## 5. Repo and config hygiene

- [ ] **A-09 · INFO · VERIFIED** — No keys or keystores are committed
  - Where: `certs/` (README only), `.gitignore` (`*.jks`, `*.p12`, `data/`, `mirage.properties`).
  - Problem: none found. The only secret-like value is the `changeit` default in code (A-02).
  - Fix: keep it this way; add a CI check that fails on committed `*.jks`, `*.p12` or `*.pem` files.

## Summary

| Severity | Count | Items |
| --- | --- | --- |
| MEDIUM | 5 | A-01, A-02, A-03, A-04, A-06 |
| LOW | 3 | A-05, A-07, A-08 |
| INFO | 1 | A-09 |

