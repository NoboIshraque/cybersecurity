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
| 1 | Networking & liveness: Console server + Sensor client, JSON protocol, heartbeats | ✅ Done (PR #1) |
| 2 | Decoys & capture: FTP/Telnet decoys, login parsing, bait-file watcher, honeytokens | ✅ Done (PR #2) |
| 3 | Detection & persistence: H2 DAO layer, password-spray rule | ✅ Done (PR #3) |
| 4 | Active defence: Console→Sensor commands, tarpit, block list | 🔨 In review (PR #4) |
| 5 | Specter red team: scan/spray/replay strategies, campaigns, stealth timing, Specter UI | ⬜ Next |
| 6 | Console dashboard (Swing): network map, alert feed, dossier, ledger-verify view | ⬜ Planned |
| 7 | Integration, testing & demo: end-to-end lab run, demo script, report screenshots | ⬜ Planned |

## Done so far

- **Phase 0** — domain model, `ScopeGuard`, hash-chained `AlertLedger`, rules, response commands, engine.
- **Phase 1** — JSON wire protocol, `ConsoleServer`, `SensorClient`, `SensorRegistry` + heartbeats; CI.
- **Phase 2** — HTTP/FTP/Telnet decoys capture logins; `BaitFileWatcher`; honeytokens.
- **Phase 3** — H2 persistence (`Database`, `EventDao`, `AlertDao`, `Persistence`); `PasswordSprayRule`.
- **Phase 4** (in review) — bidirectional protocol: the Console broadcasts scoped `ResponseCommand`s to
  Sensors (`CommandSink`, `ConsoleServer.broadcast`). Sensors hold a `DefenceState` (a `ConnectionGate`):
  blocked sources are dropped, tarpitted sources get a slow single-byte drip. Response actions now
  dispatch real commands. 26 tests green (incl. end-to-end command delivery and a blocked-source drop).

## Current / next phase — Phase 5: Specter red team

**Goal.** A controllable attacker that drives the demo, confined by `ScopeGuard` to the lab range.

- [ ] `PortScanAttack` (sweep a range of ports/decoys), `PasswordSprayAttack`, `HoneytokenReplayAttack`.
- [ ] A `Campaign` that bundles strategies into a scenario.
- [ ] Stealth timing (a configurable delay between attempts: low-and-slow vs fast-and-loud).
- [ ] The Specter Swing console (target picker restricted to the lab range, campaign selector,
      stealth slider, live attack log). GUI compiles; keep logic in testable non-GUI classes.
- [ ] Tests for the strategies/campaign logic (point them at a local decoy over loopback).

(Merge PR #4 first, then branch Phase 5 off an up-to-date `master`.)

## Decisions log

- **Topic:** deception-based IDS ("Mirage"), two-sided (blue Console + red Specter).
- **Safety:** all offence/active-defence gated by `ScopeGuard` to a lab CIDR; non-IP sources refused.
- **"Counter-attack" interpretation:** trap + slow (tarpit) + mislead (fake data) + block — all now wired.
- **Build:** single Maven module. Deps: Jackson (JSON), H2 (persistence). Java 17. GitHub Actions CI.
- **Protocol:** newline-delimited JSON both ways — `SensorMessage` (Sensor→Console), `ResponseCommand`
  (Console→Sensor). The Console pushes commands on the same socket a Sensor connected with.
- **Concurrency:** producers enqueue; `CorrelationEngine` is the single consumer/mutator.
- **Course:** Object Oriented Design and Programming-II Laboratory (ICE-2206).

## Session log

- **2026-10-13** — Scope + docs; Phase 0 bootstrap; course/instructors confirmed.
- **2026-10-13 (Phase 1)** — Networking + liveness + CI; merged PR #1.
- **2026-10-13 (Phase 2)** — Decoys & capture, honeytokens; merged PR #2.
- **2026-10-13 (Phase 3)** — H2 persistence + password-spray rule; merged PR #3.
- **2026-10-13 (Phase 4)** — Active defence: Console→Sensor commands, tarpit, block list; 26 tests
  green; opened PR #4.

## Links

- **Proposal (submission):** https://claude.ai/code/artifact/d5969a4d-9277-473a-8c66-4d32e04716df
- **Report (detailed):** https://claude.ai/code/artifact/f578aa80-d25b-4514-8adf-f03f27562c3b
- In-repo copies: `PROPOSAL.md` (proposal), `REPORT.md` (report)
