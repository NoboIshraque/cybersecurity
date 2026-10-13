# HANDOVER — Mirage

_Last updated: 2026-10-13_

Start here each session, then read `CLAUDE.md` for conventions. Update the phase statuses, the
**Current phase** actions, and the **Session log** whenever you finish work.

## How to resume in 60 seconds

1. `mvn test` — confirm the build is green.
2. Check **Development phases** below for where we are.
3. Work the top unchecked item in **Current phase**.

## Development phases

| Phase | Focus | Status |
| --- | --- | --- |
| 0 | Foundation: repo, domain model, ScopeGuard, hash-chained ledger, rules, responses, engine, tests | ✅ Done |
| 1 | Networking & liveness: TLS Console server + Sensor client, JSON protocol, heartbeats | 🔨 In progress |
| 2 | Decoys & capture: FTP/Telnet decoys, login parsing, bait-file watcher, honeytokens | ⬜ Planned |
| 3 | Detection & persistence: more rules, cross-host correlation, severity scoring, H2 DAO | ⬜ Planned |
| 4 | Active defence: response dispatch to Sensors, real tarpit, feed-fakes, block list | ⬜ Planned |
| 5 | Specter red team: scan/spray/replay strategies, campaigns, stealth timing, Specter UI | ⬜ Planned |
| 6 | Console dashboard (Swing): network map, alert feed, dossier, ledger-verify view | ⬜ Planned |
| 7 | Integration, testing & demo: end-to-end lab run, demo script, report screenshots | ⬜ Planned |

## Current phase — Phase 1: Networking & liveness

**Goal.** A Sensor connects to the Console and streams decoy events over a (TLS-capable) socket
using newline-delimited JSON; the Console feeds them into the correlation pipeline; heartbeats let
the Console detect a dead Sensor.

- [ ] Wire protocol: `SensorMessage` (EVENT / HEARTBEAT) + a JSON codec (Jackson).
- [ ] `ConsoleServer`: thread-pool accept loop; decode lines; route events → `BlockingQueue`,
      heartbeats → `SensorRegistry`. TLS via an SSL socket factory; plain sockets for local/dev.
- [ ] `SensorClient`: connect, send events and periodic heartbeats.
- [ ] `SensorRegistry` + heartbeat sweeper (`ScheduledExecutorService`) → dead-sensor alert.
- [ ] Tests: JSON round-trip, end-to-end networking over loopback, registry sweep.
- [ ] `keytool` keystore instructions in `certs/` for the real TLS run.

Built on branch `phase-1-networking` → draft PR.

## Phase 0 — done (foundation)

Domain model + `ScopeGuard` + hash-chained `AlertLedger` + detection rules + response commands +
`CorrelationEngine` + `AlertBus` + one decoy (`FakeHttpLoginDecoy`) + one attack (`BruteForceAttack`) +
H2 `db/schema.sql` + 6 passing unit tests. `master` builds clean.

## Decisions log

- **Topic:** deception-based IDS ("Mirage"), two-sided (blue Console + red Specter).
- **Safety:** all offence/active-defence is gated by `ScopeGuard` to a lab CIDR; no real DoS or
  external exploitation. Hard rule (see `CLAUDE.md`).
- **"Counter-attack" interpretation:** trap + slow (tarpit) + mislead (fake data) + block, not
  hacking back an external host.
- **Build:** single Maven module with `com.mirage.{common,console,sensor,specter}` packages.
- **Stack:** Java 17 (JDK 21 toolchain), sockets + TLS, `java.util.concurrent`, Swing, H2,
  SHA-256 ledger, JUnit 5. Phase 1 adds Jackson for the JSON wire protocol.
- **Concurrency model:** producers (network threads) enqueue; `CorrelationEngine` is the single
  consumer and the only mutator of profiles/ledger.
- **Course:** Object Oriented Design and Programming-II Laboratory (ICE-2206).

## Session log

- **2026-10-13** — Set topic + scope; wrote the **report** (Claude doc) and a separate **proposal**
  (Claude doc); bootstrapped the repo (Phase 0 foundation + 6 tests); confirmed course title
  (ICE-2206) and both instructors; split the project into 8 phases; started **Phase 1** (networking)
  on branch `phase-1-networking`.

## Links

- **Proposal (submission):** https://claude.ai/code/artifact/d5969a4d-9277-473a-8c66-4d32e04716df
- **Report (detailed):** https://claude.ai/code/artifact/f578aa80-d25b-4514-8adf-f03f27562c3b
- In-repo copies: `PROPOSAL.md` (proposal), `REPORT.md` (report)
