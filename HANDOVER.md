# HANDOVER — Mirage

_Last updated: 2026-10-13_

Start here each session, then read `CLAUDE.md` for conventions. Update the phase statuses, the
**Current phase** actions, and the **Session log** whenever you finish work.

## How to resume in 60 seconds

1. `mvn test` — confirm the build is green.
2. Check **Development phases** below for where we are.
3. Work the top unchecked item in **Current / next phase**.

## Development phases

| Phase | Focus | Status |
| --- | --- | --- |
| 0 | Foundation: repo, domain model, ScopeGuard, hash-chained ledger, rules, responses, engine, tests | ✅ Done |
| 1 | Networking & liveness: TLS Console server + Sensor client, JSON protocol, heartbeats | ✅ Done (PR #1, merged) |
| 2 | Decoys & capture: FTP/Telnet decoys, login parsing, bait-file watcher, honeytokens | 🔨 In review (PR #2) |
| 3 | Detection & persistence: more rules, cross-host correlation, severity scoring, H2 DAO | ⬜ Next |
| 4 | Active defence: response dispatch to Sensors, real tarpit, feed-fakes, block list | ⬜ Planned |
| 5 | Specter red team: scan/spray/replay strategies, campaigns, stealth timing, Specter UI | ⬜ Planned |
| 6 | Console dashboard (Swing): network map, alert feed, dossier, ledger-verify view | ⬜ Planned |
| 7 | Integration, testing & demo: end-to-end lab run, demo script, report screenshots | ⬜ Planned |

## Done so far

- **Phase 0** — domain model, `ScopeGuard`, hash-chained `AlertLedger`, detection rules, response
  commands, `CorrelationEngine`, `AlertBus`, H2 `db/schema.sql`, unit tests.
- **Phase 1** (merged) — JSON wire protocol (`SensorMessage` + `JsonCodec`), `ConsoleServer`
  (thread-pool, TLS-capable via a factory swap), `SensorClient`, `SensorRegistry` + heartbeat
  sweeper. GitHub Actions CI runs `mvn verify` on every PR and on `master`.
- **Phase 2** (in review) — `Decoy` refactored to read input and emit richer events; `FakeHttpLoginDecoy`
  now parses Basic-auth; new `FakeFtpDecoy` and `FakeTelnetDecoy` capture `LOGIN_ATTEMPT`s;
  `BaitFileWatcher` (WatchService) emits `FILE_CHANGE`; `Honeytoken` + `HoneytokenStore` with a
  store-backed `HoneytokenUseRule`; new `BaitFileRule`. `ScopeGuard` hardened (non-IP sources are
  refused, never throw in `isAuthorised`) and the engine no longer dies on a non-network alert.
  16 tests green.

## Current / next phase — Phase 3: Detection & persistence

**Goal.** Persist state in the embedded H2 database and deepen detection.

- [ ] H2 + a small JDBC DAO layer; load `db/schema.sql` on Console startup.
- [ ] Persist sensors, attack events, attacker profiles, alerts and block rules.
- [ ] Cross-host correlation (one source touching decoys on several sensors) + severity scoring.
- [ ] A password-spray rule (one password across many usernames).
- [ ] Tests for the DAO round-trips and the new rules.

(Merge PR #2 first, then branch Phase 3 off an up-to-date `master`.)

## Decisions log

- **Topic:** deception-based IDS ("Mirage"), two-sided (blue Console + red Specter).
- **Safety:** all offence/active-defence is gated by `ScopeGuard` to a lab CIDR; non-IP sources
  (e.g. bait-file events) are refused and simply get no network response. Hard rule (see `CLAUDE.md`).
- **"Counter-attack" interpretation:** trap + slow (tarpit) + mislead (fake data) + block.
- **Build:** single Maven module, `com.mirage.{common,console,sensor,specter}`. Jackson for the
  JSON wire protocol. Java 17 (JDK 21 toolchain). CI via GitHub Actions.
- **Concurrency model:** producers (network/decoy threads) enqueue; `CorrelationEngine` is the
  single consumer and only mutator of profiles/ledger.
- **Course:** Object Oriented Design and Programming-II Laboratory (ICE-2206).

## Session log

- **2026-10-13** — Topic + scope; wrote the report and the proposal (Claude docs); bootstrapped the
  repo (Phase 0); confirmed course (ICE-2206) + instructors; split into 8 phases.
- **2026-10-13 (Phase 1)** — Networking + liveness; added CI; merged as PR #1.
- **2026-10-13 (Phase 2)** — Decoys & capture: 3 decoys with login parsing, bait-file watcher,
  honeytokens; hardened ScopeGuard + engine; 16 tests green; opened PR #2.

## Links

- **Proposal (submission):** https://claude.ai/code/artifact/d5969a4d-9277-473a-8c66-4d32e04716df
- **Report (detailed):** https://claude.ai/code/artifact/f578aa80-d25b-4514-8adf-f03f27562c3b
- In-repo copies: `PROPOSAL.md` (proposal), `REPORT.md` (report)
