# HANDOVER — Mirage

_Last updated: 2026-10-13_

This is the living status of the project. **Start here each session**, then read `CLAUDE.md`
for conventions. Update the "Session log" and the checklists below whenever you finish work.

## How to resume in 60 seconds

1. `mvn test` — confirm the build is green (6 tests should pass).
2. Read the **Status** checklist below to see what's done and what's next.
3. Pick the top unchecked item under **Next steps** and go.

## Status

**Phase: foundation scaffolded. Proposal submitted-ready.**

Done
- [x] Project topic, scope, and safety boundary decided
- [x] Full written proposal (all template sections + 5 figures) — see **Links**
- [x] Maven project + package layout (`common / console / sensor / specter`)
- [x] Core domain types: `AttackEvent`, `AttackerProfile`, `Classification`, `Alert`, `LedgerEntry`, enums
- [x] `ScopeGuard` (the safety boundary) with CIDR matching + tests
- [x] `AlertLedger` (hash-chained, tamper-evident) + tests
- [x] Detection rules: brute-force, port-scan, honeytoken + tests
- [x] Response commands (tarpit / feed-fakes / block) + `ResponsePolicy` (scope-guarded)
- [x] `CorrelationEngine` (producer–consumer) + `AlertBus` (observer)
- [x] `Decoy` abstract + `FakeHttpLoginDecoy`; `AttackStrategy` + `BruteForceAttack` + `Attacker`
- [x] H2 `db/schema.sql`
- [x] 6 unit tests passing, `master` builds clean

Not started (see Next steps for order)
- [ ] Networking: TLS Console server + Sensor client + JSON wire protocol
- [ ] More decoys (FTP, Telnet) + real login-attempt parsing in the HTTP decoy
- [ ] Bait-file watcher (WatchService) + honeytokens
- [ ] Response dispatch Console→Sensor + real tarpit on the Sensor side
- [ ] Heartbeats + SensorRegistry (detect dead sensors)
- [ ] H2 persistence via a JDBC DAO layer (load `db/schema.sql`)
- [ ] Swing: Console dashboard + Specter console
- [ ] More Specter strategies (port-scan, spray, honeytoken replay) + Campaign + stealth timing

## Next steps (in order)

1. **Wire the network.** Build `console.ConsoleServer` (TLS `SSLServerSocket` + thread pool),
   a Sensor-side client, and a newline-delimited JSON protocol for `AttackEvent`. Feed received
   events into the Console's `BlockingQueue` so `CorrelationEngine` processes them. Generate a
   self-signed lab keystore with `keytool` (document it in `certs/`, keep the `.jks` gitignored).
2. **Flesh out decoys.** Add `FakeFtpDecoy` and `FakeTelnetDecoy`; make `FakeHttpLoginDecoy`
   parse the `Authorization` header and emit `LOGIN_ATTEMPT` events with the attempted creds.
3. **Bait files + honeytokens.** A `WatchService` thread that emits `FILE_CHANGE`, and a planted
   honeytoken that emits `HONEYTOKEN_USE`.
4. **Close the response loop.** Send `ResponseAction`s from Console to the owning Sensor and run
   the real tarpit (slow single-byte drip) and the block list there.
5. **Persistence.** Add H2 + a small DAO layer; load `db/schema.sql` on Console startup.
6. **Swing UIs.** Console dashboard (network map, alert feed, dossier, ledger status) using
   `SwingWorker`/`invokeLater`; then the Specter console.
7. **More Specter.** `PortScanAttack`, `PasswordSprayAttack`, `HoneytokenReplayAttack`,
   a `Campaign` bundler, and a stealth (timing) control.

## Decisions log

- **Topic:** deception-based IDS ("Mirage"), two-sided (blue Console + red Specter).
- **Safety:** all offence/active-defence is gated by `ScopeGuard` to a lab CIDR; no real DoS
  or external exploitation. This is a hard rule (see `CLAUDE.md`).
- **"Counter-attack" interpretation:** trap + slow (tarpit) + mislead (fake data) + block,
  not hacking back an external host.
- **Build:** single Maven module with `com.mirage.{common,console,sensor,specter}` packages
  (the proposal's multi-jar layout is the conceptual/eventual structure).
- **Stack:** Java 17 language level (JDK 21 toolchain), sockets + TLS, `java.util.concurrent`,
  Swing, embedded H2, SHA-256 ledger, JUnit 5. No third-party libs yet (ScopeGuard CIDR and the
  ledger are implemented with the JDK only).
- **Concurrency model:** producers (network threads) enqueue; `CorrelationEngine` is the single
  consumer and the only mutator of profiles/ledger.

## Session log

- **2026-10-13** — Set the topic and scope; wrote the full proposal (Claude artifact) and this
  repo: Maven project, the core domain + ScopeGuard + ledger + rules + responses + engine +
  one decoy + one attack, H2 schema, and 6 passing tests. Next: wire the TLS network (step 1).

## Links

- **Proposal (formatted master, with rendered figures):**
  https://claude.ai/code/artifact/f578aa80-d25b-4514-8adf-f03f27562c3b
- In-repo proposal copy: `PROPOSAL.md`
