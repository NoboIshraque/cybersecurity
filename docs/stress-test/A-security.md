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

- [ ] **A-01 · CRITICAL · VERIFIED** — ScopeGuard authorises strings that Java then resolves as host names (anywhere)
  - Where: `src/main/java/com/mirage/common/ScopeGuard.java:105-115` (`toLong`); used by `specter/Attacker.java:52,63` → `PortScanAttack.java:22`, `BruteForceAttack.java:47`, `PasswordSprayAttack.java:45`, `HoneytokenReplayAttack.java:24` (`new Socket(target.host(), …)`).
  - Problem: `toLong` checks the *string* with `split("\.")` + `Integer.parseInt(o) & 0xFF`. That accepts octets > 255 and negative octets (wrapped by `& 0xFF`), a leading `+`, non-ASCII digits, and trailing dots (`split` drops trailing empty parts). Java's `InetAddress` does not treat those strings as IPv4 literals, so `new Socket(host, port)` sends them to the name resolver (hosts file / DNS / LLMNR). Whoever answers that lookup chooses the real destination, so Specter or a counter can be steered to any address on the internet. The guard checks one thing and the socket connects to another.
  - Evidence: harness `ScopeProbe` (default range `10.10.0.0/24,127.0.0.1/32`), run with `-Djdk.net.hosts.file=hosts.txt` so the lookup is answered from a local file and nothing goes on the wire: `10.10.0.261`, `10.10.0.-251`, `+10.10.0.5`, `10.10.0.5.`, `127.0.0.1.`, `127.0.0.257` → `guard=ALLOW` and `java-resolves-to=8.8.8.8`. `10.10.0.5...` and Arabic-Indic digits are also ALLOWed (they fall through to the resolver). In the Console the `sourceIp` of a counter comes straight from an EVENT line, which any client can forge (see A-04), so this is reachable remotely, not only by typing into Specter's target box.
  - Fix: parse strictly. Require `^(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(…)){3}$` (ASCII digits, no sign, no leading zeros, no trailing dot), or use `InetAddress.ofLiteral` (JDK 22+) or your own byte parser. Then hand the *parsed* `InetAddress` to the strategies (`Target` should hold an `InetAddress`, not a `String`) so the address that was checked is the address that gets connected to. Add these strings to `ScopeGuardTest` as must-deny cases.

- [ ] **A-02 · MEDIUM · VERIFIED** — `new ScopeGuard(...)` accepts any prefix; `/0`, `/33`, `/97` authorise every IPv4 address
  - Where: `ScopeGuard.java:24-28` (constructor) and `:96-102` (`parse`: `0xFFFFFFFFL << (32 - prefix)`, and Java masks the shift count to 6 bits).
  - Problem: only `requireLabRange` validates the range. The public constructor does not, and a prefix > 32 gives a negative shift count that wraps to a mask of 0, which matches everything. Today only `LabConfig.scopeGuard()` builds guards in `src/main`, so production is safe, but `tools/screenshots/*.java` and any future caller or test that uses the constructor bypasses the check. A default-deny safety class should not have a fail-open constructor.
  - Evidence: `ScopeProbe`: `new ScopeGuard(List.of("10.10.0.0/33")).isAuthorised("8.8.8.8") = true`; the same for `/0` and `/97`.
  - Fix: call `validateLabCidr` from the constructor (or make the constructor private and expose only `ScopeGuard.forLab(cidrs)`). In `parse`, reject prefixes outside 0..32.

- [ ] **A-03 · LOW · VERIFIED** — The "/16 or narrower" limit applies per CIDR, not in total; sloppy CIDR text is accepted
  - Where: `ScopeGuard.java:57-94`.
  - Problem: listing 256 `/16`s (`10.0.0.0/16 … 10.255.0.0/16`) passes and covers all of 10/8. Adding the 172.16/12 and 192.168/16 blocks the same way covers all RFC 1918 space. The text parsing is also loose: `266.10.0.0/24` (wraps to 10.10.0.0/24), `10.10.0.0/+16` and `10.10.0.0/24/8` are all accepted, so the range the logs print is not the range in force.
  - Evidence: `ScopeProbe`: the 256 × /16 list is accepted and `isAuthorised("10.200.3.4") = true`. The three strings above print `ACCEPTED`.
  - Fix: cap the total number of addresses (for example ≤ 65 536) and the number of CIDRs. Use the strict parser from A-01 for CIDRs too, require exactly one `/`, and print the normalised range.


> **Note:** agent A stopped after section 1. The main session finished sections 2–7 by code review
> (file:line references checked against `feature/live-shells` @ `22f951e`). These items are
> SUSPECTED unless marked otherwise and need a reproduction or a test when fixed.

## 2. Sensor ↔ Console protocol

- [ ] **A-04 · HIGH · SUSPECTED** — Sensors are not authenticated; any client that reaches the Console port is trusted
  - Where: `console/ConsoleServer.java:93-160` (`acceptLoop`, `handle`, `route`); `common/TlsFactories.java:30-40` (server context has no client auth).
  - Problem: the Console accepts every TCP (or one-way TLS) connection and trusts its EVENT, HEARTBEAT and ACK lines. The `sensorId` is whatever the peer claims, and an EVENT's `sourceIp` is taken as-is. A peer on the LAN can therefore feed the engine invented events (raising alerts and, with auto-response on, scoped responses against in-range addresses), add entries to the ledger, impersonate a real sensor, and send ACKs that flip a threat popup to NEUTRALISED without anything being blocked. Together with A-01, a forged `sourceIp` is also the input the counter-offensive acts on.
  - Fix: require mutual TLS (`setNeedClientAuth(true)` with a lab CA and one client cert per Sensor) and bind the `sensorId` to the certificate; at minimum add a shared enrolment token checked in the first message. Accept ACKs only for commands this Console actually sent (track pending command IDs). Validate `sourceIp` with the strict parser from A-01 and drop events whose source is not a well-formed IPv4 literal.

- [ ] **A-05 · MEDIUM · SUSPECTED** — No line-length limit and an unbounded thread pool on the Console server
  - Where: `ConsoleServer.java:67` (`Executors.newCachedThreadPool()`), `:118-123` (`BufferedReader.readLine()` with no cap); `sensor/SensorClient.java:60` (same on the Sensor side).
  - Problem: one connection that never sends a newline makes `readLine` buffer without limit (memory exhaustion), and every connection gets its own thread with no upper bound, so a burst of connections exhausts threads. Either stops the Console during a demo.
  - Fix: read with a bounded reader (reject lines over e.g. 64 KiB and close the connection), use a fixed-size pool, cap concurrent connections, and set a socket read timeout tied to the heartbeat interval.

- [ ] **A-06 · MEDIUM · SUSPECTED** — TLS is optional, server-only, and defaults to the password `changeit`
  - Where: `ConsoleMain.java:143-158`, `SensorMain.java:90-104`, `TlsFactories.java`.
  - Problem: with no `-Dmirage.keystore`, the link is plain TCP, which is the default in every script and in the two-laptop guide. With TLS, only the server is authenticated (see A-04). A keystore that fails to load silently falls back to plain sockets. Keystore and truststore passwords default to `changeit`.
  - Fix: make the two-laptop scripts use TLS by default; fail closed (refuse to start) when a configured keystore cannot load, instead of falling back to plaintext; drop the `changeit` default and read the password from an env var or prompt; document generating the lab CA in the setup guide.

- [ ] **A-07 · MEDIUM · SUSPECTED** — A Sensor obeys commands from whatever host it connected to
  - Where: `sensor/SensorClient.java:50-66`, `SensorMain.java:30-45`.
  - Problem: without TLS the Sensor cannot tell its Console from an impostor on `console.host:console.port` (for example after the real Console is down, or with a spoofed address on the LAN). An impostor could push BLOCK/TARPIT/UNBLOCK to the decoys, e.g. UNBLOCK an attacker the operator just blocked.
  - Fix: covered by mandatory TLS with a pinned lab CA (A-06). Also log every applied command with its origin, and consider signing commands with the Console's key.

## 3. Decoys and the bait watcher

- [ ] **A-08 · MEDIUM · SUSPECTED** — Twelve slow or tarpitted connections stall a decoy for everyone
  - Where: `sensor/Decoy.java:29,35` (fixed pool of 12), `:104-118` (tarpit loops until the peer closes); `FakeHttpLoginDecoy.java:46`, `FakeFtpDecoy.java:31`, `FakeTelnetDecoy.java:29-31` (`readLine` with no socket timeout).
  - Problem: a worker is held for as long as the peer keeps the socket open, both for an idle client that never sends a line and for a tarpitted client (by design the tarpit never lets go). Twelve such connections fill the pool, so new connections queue unserved and their events are not reported until a worker frees up. A tarpit meant to slow one attacker ends up freezing the decoy.
  - Fix: set `setSoTimeout` on every accepted socket (e.g. 10–15 s for interaction); cap tarpit hold time and connections per source; run tarpits on a separate small pool or with non-blocking I/O so they cannot starve normal service; report the CONNECT event before queuing.

- [ ] **A-09 · LOW · SUSPECTED** — Bait-file events depend on the watcher only; no integrity baseline
  - Where: `sensor/BaitFileWatcher.java:32-60`.
  - Problem: `WatchService` can drop events (OVERFLOW) and misses changes made while the Sensor is down. There is no hash baseline, so a file replaced while the Sensor was off is never flagged. The bait directory is also relative to the working directory.
  - Fix: record a SHA-256 baseline of each bait file at start and re-check it on a timer and on OVERFLOW; report missing or changed files; make the bait directory explicit in `LabConfig`.

## 4. Ledger and persistence

- [ ] **A-10 · MEDIUM · SUSPECTED** — The ledger signing key is new on every start and never stored, so stored signatures cannot be verified later
  - Where: `console/KeyPairSigner.java:24-33`, `ConsoleMain.java:61`, `console/AlertLedger.java:36-56`.
  - Problem: each Console run generates a fresh ECDSA key pair in memory, and the public key is not persisted or published. Alerts written to H2 by an earlier run carry signatures nobody can check, and the hash chain is unkeyed SHA-256, so someone who can edit the H2 file can rewrite the chain consistently. The "tamper-evident" claim holds only within one run.
  - Fix: persist the key pair in a password-protected PKCS12 keystore (or at least export the public key with a key ID stored in each row); verify the stored chain at start-up; add a `verify` for rows loaded from H2.

- [ ] **A-11 · LOW · SUSPECTED** — The H2 database has no password and lives in the working directory
  - Where: `console/db/Database.java:17`, `ConsoleMain.java:63` (`jdbc:h2:file:./data/mirage`).
  - Problem: anyone with local file access can open or alter the alert and event history; combined with A-10, an edited history goes undetected. (Inserts use `PreparedStatement`; no SQL injection was found in the DAOs.)
  - Fix: set a DB user and password (from config or env), keep the DB in a per-user data directory, and rely on the A-10 verification to detect edits.

## 5. Console UI text

- [ ] **A-12 · MEDIUM · SUSPECTED** — An event's source string reaches Swing components that render HTML
  - Where: `console/ui/Dashboard.java` (the alert table uses `DefaultTableCellRenderer`, a `JLabel`), `NetworkMapPanel`, `ThreatPopup` title/labels; the string comes from an EVENT line (`ConsoleServer.route`).
  - Problem: Swing renders label text that starts with `<html>` as HTML, including loading images by URL. A forged event (A-04) whose `sourceIp` is HTML would be rendered in the alert table, and an embedded image reference would make the Console open a connection to an arbitrary address, one that never passes ScopeGuard.
  - Fix: reject non-IPv4 sources at the server (A-04 fix), and set `putClientProperty("html.disable", Boolean.TRUE)` on labels and renderers that show untrusted text (or escape it).

## 6. Attacker-side counter-watch

- [ ] **A-13 · LOW · SUSPECTED** — CounterWatch spawns a thread per connection with no cap
  - Where: `specter/CounterWatch.java:28` (`newCachedThreadPool`), `observe()`.
  - Problem: the listener is reachable by anyone on the LAN; each connection costs a thread for up to the 400 ms drain, with no limit on concurrent connections or log volume, so a burst can flood the shell and the thread table on Laptop B.
  - Fix: a fixed-size pool plus a per-source connection cap and rate-limited logging; bind it only on the lab interface.

## 7. Repo and config hygiene

- [ ] **A-14 · INFO · VERIFIED** — No keys or keystores are committed
  - Where: `certs/` (README only), `.gitignore` (`*.jks`, `*.p12`, `data/`, `mirage.properties`).
  - Problem: none found. The only secret-like value is the `changeit` default in code (A-06).
  - Fix: keep it this way; add a CI check that fails on committed `*.jks`, `*.p12` or `*.pem` files.

## Summary

| Severity | Count | Items |
| --- | --- | --- |
| CRITICAL | 1 | A-01 |
| HIGH | 1 | A-04 |
| MEDIUM | 7 | A-02, A-05, A-06, A-07, A-08, A-10, A-12 |
| LOW | 4 | A-03, A-09, A-11, A-13 |
| INFO | 1 | A-14 |

## Not covered

- No live reproduction of A-04 to A-13 (agent A stopped before that part, and the main session did
  not build attack tooling). Each fix should come with a unit or integration test showing the
  issue is closed.
- IPv6 handling beyond "not parsed" (ScopeGuard rejects non-IPv4 strings except the cases in A-01).
- No dependency CVE scan (Jackson 2.17.1, H2 2.2.224).
