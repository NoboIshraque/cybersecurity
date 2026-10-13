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
| 0 | Foundation: domain model, ScopeGuard, hash-chained ledger, rules, responses, engine, tests | ✅ Done |
| 1 | Networking & liveness: TLS Console server + Sensor client, JSON protocol, heartbeats | ✅ Done (PR #1) |
| 2 | Decoys & capture: FTP/Telnet decoys, login parsing, bait-file watcher, honeytokens | ✅ Done (PR #2) |
| 3 | Detection & persistence: H2 DAO layer, password-spray rule | 🔨 In review (PR #3) |
| 4 | Active defence: response dispatch to Sensors, real tarpit, feed-fakes, block list | ⬜ Next |
| 5 | Specter red team: scan/spray/replay strategies, campaigns, stealth timing, Specter UI | ⬜ Planned |
| 6 | Console dashboard (Swing): network map, alert feed, dossier, ledger-verify view | ⬜ Planned |
| 7 | Integration, testing & demo: end-to-end lab run, demo script, report screenshots | ⬜ Planned |

## Done so far

- **Phase 0** — domain model, `ScopeGuard`, hash-chained `AlertLedger`, detection rules, response
  commands, `CorrelationEngine`, `AlertBus`, unit tests.
- **Phase 1** — JSON wire protocol, `ConsoleServer` (TLS-capable), `SensorClient`, `SensorRegistry`
  + heartbeat sweeper. GitHub Actions CI (`mvn verify` on every PR and on `master`).
- **Phase 2** — `Decoy` reads input and emits richer events; HTTP/FTP/Telnet decoys capture logins;
  `BaitFileWatcher`; `Honeytoken` + `HoneytokenStore` + store-backed `HoneytokenUseRule`; `BaitFileRule`.
- **Phase 3** (in review) — embedded **H2** persistence: `Database` (loads `schema.sql` from the
  classpath), `EventDao`, `AlertDao`, a `Persistence` interface (+ `H2Persistence`, `NONE`) wired into
  the engine so every event and alert is saved; new `PasswordSprayRule` (one password across many
  users, T1110.003). 19 tests green.

## Current / next phase — Phase 4: Active defence

**Goal.** Close the loop: the Console sends scoped responses to the Sensors, which carry them out.

- [ ] Extend the wire protocol so the Console can send a `ResponseCommand` to a Sensor.
- [ ] Real tarpit on the Sensor (hold the attacker's socket, drip one byte at a time).
- [ ] Feed-fake-data response (serve junk instead of closing).
- [ ] A network-wide block list the Sensors enforce (drop connections from blocked sources).
- [ ] Make `TarpitAction` / `FeedFakeDataAction` / `BlockSourceAction` actually dispatch to Sensors.
- [ ] Tests for the dispatch protocol and the block list.

(Merge PR #3 first, then branch Phase 4 off an up-to-date `master`.)

## Decisions log

- **Topic:** deception-based IDS ("Mirage"), two-sided (blue Console + red Specter).
- **Safety:** all offence/active-defence gated by `ScopeGuard` to a lab CIDR; non-IP sources refused
  and get no network response. Hard rule (see `CLAUDE.md`).
- **"Counter-attack" interpretation:** trap + slow (tarpit) + mislead (fake data) + block.
- **Build:** single Maven module, `com.mirage.{common,console,sensor,specter}`. Deps: Jackson (JSON),
  H2 (persistence). Java 17 (JDK 21 toolchain). CI via GitHub Actions.
- **Persistence:** embedded H2; schema is `src/main/resources/schema.sql` (runtime) mirrored by
  `db/schema.sql` (docs). The engine persists through a `Persistence` interface (`NONE` for tests).
- **Concurrency model:** producers enqueue; `CorrelationEngine` is the single consumer and only
  mutator of profiles/ledger.
- **Course:** Object Oriented Design and Programming-II Laboratory (ICE-2206).

## Session log

- **2026-10-13** — Scope + docs (report, proposal); Phase 0 bootstrap; course/instructors confirmed.
- **2026-10-13 (Phase 1)** — Networking + liveness + CI; merged PR #1.
- **2026-10-13 (Phase 2)** — Decoys & capture, honeytokens; merged PR #2.
- **2026-10-13 (Phase 3)** — H2 persistence + password-spray rule; 19 tests green; opened PR #3.

## Links

- **Proposal (submission):** https://claude.ai/code/artifact/d5969a4d-9277-473a-8c66-4d32e04716df
- **Report (detailed):** https://claude.ai/code/artifact/f578aa80-d25b-4514-8adf-f03f27562c3b
- In-repo copies: `PROPOSAL.md` (proposal), `REPORT.md` (report)
