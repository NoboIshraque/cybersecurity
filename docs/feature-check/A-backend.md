# A — headless, network and back-end feature check

- **Repo:** `E:\cybersecurity`, branch `feature/live-shells` @ `b6d7f23` (unchanged throughout).
- **Date:** 2026-11-08
- **JDK:** 25.0.1 (Temurin/Oracle `jdk-25`); build via committed Maven Wrapper (`mvnw.cmd`, Maven 3.9.9).
- **What was run:** `mvnw -B test` x2; `mvnw -B -DskipTests package`; the fat jar in `defender` /
  `console` / `sensor` roles (headless via `-Djava.awt.headless=true`); the `.bat` run scripts
  (copies, from my scratch dir); raw-socket decoy probes (PowerShell `TcpClient`); and five small
  JVM harnesses compiled against `target/classes` (config/ScopeGuard, Specter strategies+responses,
  counter-watch/counter-offensive, TLS, rule boundaries, H2 DB dump/tamper). Everything ran with a
  working dir inside my scratchpad; the repo tree stayed clean (see end).
- **Ports used:** 94xx / 95xx only, to stay clear of Agent B's defaults (8443/8081/8021/8023/8080).
- **Not in scope / not tested (documented as known):** real two-laptop LAN, IPv6, dependency CVE
  scan. Also the Swing GUI (Agent B's area).

## Summary

**34 back-end features checked — 32 PASS, 2 PASS-with-note, 0 FAIL. 2 findings, both LOW.**
No HIGH or MEDIUM issues. Every headless / network / persistence / safety path I exercised behaved
as the docs describe. The two findings are a doc gap plus a cosmetic build-warning note.

## Feature results

| # | Feature | How tested | Result | Finding |
|---|---------|-----------|--------|---------|
| 1 | `mvnw test` baseline (x2, flakiness) | `mvnw -B test` twice | PASS — 265/265 both runs, no flaky tests (56 s / 41 s) | |
| 2 | `mvnw package` → fat jar | `mvnw -B -DskipTests package` | PASS — `target/mirage.jar` 5.2 MB; only Maven's own JDK-25 JVM warnings | FA-02 |
| 3 | Launcher: no-args / `help` usage | `java -jar mirage.jar`, `… help` | PASS — prints roles+flags, exit 0 | |
| 4 | Launcher: bad role | `java -jar mirage.jar bogus` | PASS — "Unknown role", usage, exit 2 | |
| 5 | Headless decision | `GraphicsEnvironment.isHeadless()`; `-Djava.awt.headless=true` | PASS — headless ⇒ no dashboard, auto-response ON | |
| 6 | `defender` role headless | real run, 94xx ports | PASS — Console+Sensor one process, sensor connects over 127.0.0.1 | |
| 7 | `console` / `sensor` roles alone | real runs | PASS — console binds, sensor connects | |
| 8 | Config: flags vs properties vs defaults | JVM harness + live `mirage.properties` | PASS — flags override file override defaults | |
| 9 | Config: unknown flag / no value / bad port refused | harness (`parseArgs`, `portValue`) | PASS — all throw with clear message | |
| 10 | Config: `--lab-cidrs` alias, inline `#` comment, `--sensor-id` | harness + props file | PASS | |
| 11 | ScopeGuard `requireLabRange` (public / >`/16` / empty refused; private `/16`–`/32` ok) | harness | PASS — public, `/8`, `/15`, empty all refused; `/24`+loopback, `/16` accepted | |
| 12 | ScopeGuard `require` / `isAuthorised` (in-range ok, 8.8.8.8 & malformed refused) | harness | PASS — incl. octet-overflow `999.*` not authorised | |
| 13 | `mirage.properties.example` works as documented | loaded + `scopeGuard()` | PASS — parses, range valid | |
| 14 | Console↔Sensor link: connect + sensor id shown | live defender | PASS — `sensor-<host>-<httpport>` derived id | |
| 15 | Sensor reconnect after Console killed+restarted | live: kill console, watch sensor | PASS — "lost … reconnecting" then "connected" when console returns | |
| 16 | Console restart reloads + continues chain | restart on same data dir | PASS — "1 stored alert loaded, chain intact" | |
| 17 | Two Sensors on one Console (distinct ids/ports) | live `lab-a` + `lab-b` | PASS | |
| 18 | TLS resolve: missing/wrong pw, missing file fail-closed; plain refuses remote console | `LinkSecurity` harness (scratch keystore) | PASS — never falls back to plain text | |
| 19 | TLS end-to-end mutual handshake | live console+sensor with `-Dmirage.keystore` + `MIRAGE_TLS_PASSWORD` | PASS — both "mutual TLS, pinned to the lab certificate"; sensor connected | |
| 20 | TLS fail-closed live (wrong/missing pw) | live sensor | PASS — refuses to start, explicit message | |
| 21 | `MIRAGE_LEDGER_PASSWORD` encrypts key; wrong pw refused | live console, inspect PEM | PASS — `BEGIN ENCRYPTED PRIVATE KEY`; wrong pw ⇒ refuse | |
| 22 | `MIRAGE_DB_PASSWORD` encrypts DB; wrong pw refused | live console | PASS — "encrypted"; wrong pw ⇒ refuse; correct ⇒ reload | |
| 23 | Decoy FTP by hand | raw TCP | PASS — 220 / 331 / 530, login captured | |
| 24 | Decoy Telnet by hand | raw TCP | PASS — login/Password prompts, "Login incorrect" | |
| 25 | Decoy HTTP login by hand (401 / trap→200) | raw HTTP Basic-auth | PASS — wrong ⇒ 401; trap `svc_backup:S3cr3tBackup!` ⇒ 200 fake admin page | |
| 26 | Bait files planted + tamper alert | edit + delete under `bait/` | PASS — modify ⇒ T1485 CRITICAL; delete captured; one alert/source | |
| 27 | Detection rules at the boundary | rule harness (4v5, 2v3, 1v2 svc, 1v2 sensors) + windowed | PASS — 9/9, incl. slow-spread brute = no alert | |
| 28 | Detection live end-to-end | live scan→MEDIUM, honeytoken→CRITICAL, lateral→HIGH, bait→CRITICAL | PASS | |
| 29 | Honeytoken use is CRITICAL & one BLOCK | live trap credential | PASS — T1078 CRITICAL, single block | |
| 30 | Response: tarpit / feed-fake / block / unblock + honest Specter outcome | Specter harness driving `DefenceState` | PASS — STALLED / ACCESS GRANTED (feed-fake beats tarpit) / CONNECTION BLOCKED / ACCESS GRANTED | |
| 31 | Blocked decoy ⇒ TCP reset (not silent) | live re-probe after block | PASS — "forcibly closed by remote host" = DROPPED | |
| 32 | Auto-response on when headless; policy MEDIUM→tarpit, HIGH→feed-fake, CRITICAL→block | live | PASS — observed all three; non-IP bait source handled gracefully (`[info] no network response … outside lab range`, no crash) | |
| 33 | Specter strategies + ScopeGuard refusal + bad port | harness | PASS — 8.8.8.8 refused (no traffic sent); Target(…,70000) refused before launch | |
| 34 | Counter-watch accept-and-log only; counter-offensive in-range only | harness | PASS — prober gets NO_REPLY, never written back; counter mirrors 127.0.0.1, refuses 8.8.8.8 (single & simultaneous) | |
| 35 | Ledger: hash chain + signatures persist & continue across restart | live restart | PASS — chain intact after restart | |
| 36 | Ledger: tamper detected on start | edited a stored alert in a copied DB, restarted | PASS — "chain BROKEN at entry #0 (changed since it was written)" | |
| 37 | DB tables actually written | H2 jar dump of live DB | PASS — alert(3), attack_event(17), block_rule(2 incl. TARPIT+BLOCK), honeytoken(USED=TRUE), sensor(ONLINE), attacker_profile(2) | |
| 38 | Run scripts (`run-defender.bat`, `run-attacker.bat`) | copies run from scratch, headless | PASS — defender reads `mirage.properties`, flag overrides file; attacker headless prints GUI+ScopeGuard note | |
| 39 | Robustness: garbage / oversized on Console port | raw bytes incl. 20 KB line | PASS — console stays up & listening, no exception, legit sensor stays connected | |
| 40 | Robustness: decoy flood + oversized input | 60 rapid connects + 30 KB input | PASS — console & decoys stay up, events bounded, no crash | |

(PASS-with-note rows: #2 build warnings → FA-02; #32/#38 ran under a stale-lock caveat → FA-01.)

## Findings

### FA-01 · LOW · Hard-killed defender leaves a stale H2 lock; not in the troubleshooting table

- **Where:** `console/db/Database.java:openFile` (error code 90020 → `IllegalStateException`); doc
  `docs/TWO-LAPTOP-SETUP.md` "Troubleshooting" table.
- **Steps:** Start `defender`, terminate the JVM abruptly (Task Manager / `Stop-Process -Force`,
  not Ctrl+C), then start a `defender`/`console` again in the same folder.
- **Expected / Actual:** Expected: restart succeeds (previous process is gone). Actual: it refuses
  with `Defender not started: data\mirage is in use: another Mirage Console (or defender) is already
  running in this folder.` until the stale H2 lock clears. A clean exit (Ctrl+C or closing the
  window) releases it fine — this only bites a force-kill.
- **Evidence:** reproduced twice; a clean `console` restart in the same folder loaded the chain
  normally, while a force-kill + immediate restart printed the "in use" line and the new JVM exited.
- **Suggested fix:** Add one row to the troubleshooting table for the `data\… is in use` message
  ("a defender was force-killed; wait a moment or delete the stale lock"), or open the DB with
  `AUTO_RECONNECT`/retry. Purely a rehearsal-smoothness / doc item; the message itself is clear.

### FA-02 · LOW · Maven wrapper prints JDK-25 native-access / `sun.misc.Unsafe` warnings

- **Where:** `mvnw.cmd -DskipTests package` output (jansi / guava inside Maven 3.9.9 on JDK 25).
- **Steps:** Build the jar on the JDK-25 demo laptop.
- **Expected / Actual:** Build succeeds (it does, `BUILD SUCCESS`), but stderr shows
  `WARNING: A restricted method in java.lang.System has been called` and
  `sun.misc.Unsafe::objectFieldOffset … will be removed`. These come from **Maven's own** runtime,
  not from Mirage code, so they are cosmetic — but a presenter building live may notice them.
- **Evidence:** warnings reference `…/apache-maven-3.9.9/lib/jansi-2.4.1.jar` and `guava-33.2.1`.
- **Suggested fix:** None needed for correctness. If the noise bothers anyone, pre-build the jar
  before the demo (as the docs already recommend) so the audience never sees the build.

## Notes (not findings)

- **Loopback source collapse (test-environment only):** on Windows every connection to any `127.x`
  address is reported by the decoys as source `127.0.0.1`, so I could not simulate multiple distinct
  attacker IPs via loopback on one machine. This is OS behaviour, and the documented one-laptop demo
  uses `127.0.0.1` throughout, so it does not affect the product. Multi-source logic was instead
  exercised with synthetic-IP harnesses (all pass).
- **`CLAUDE.md` staleness** ("27 checks", "JDK 21", "Current work: stress-test fixes") is **already
  acknowledged in `HANDOVER.md`** as intentionally left to the owner — not raised as a finding.
  *(Editor's note on merging: this is out of date. `CLAUDE.md` at `b6d7f23` already says 28 checks
  and JDK 17; it is HANDOVER's "Left to do" item 3 that is stale. See FB-18.)*
- **Default `lab.cidrs` = `10.10.0.0/24,127.0.0.1/32`:** on a machine whose LAN is `192.168.x`, the
  console correctly prints "No LAN address inside the lab range … single-machine demo on 127.0.0.1";
  two-laptop use requires `mirage.properties` as documented. Working as designed.

## Repo hygiene

- `git status --porcelain` → only `?? .claude/` (Agent B's worktree dir; present from session start,
  not created by me). No tracked changes; HEAD still `b6d7f23`.
- No stray `data/`, `bait/`, `bait.sha256`, or `mirage.properties` in the repo tree; `certs/` holds
  only `README.md` (the test keystore was generated in my scratch dir). `target/mirage.jar` is
  expected build output.
