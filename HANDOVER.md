# HANDOVER — Mirage

_Last updated: 2026-10-13_

Start here each session, then read `CLAUDE.md` for conventions.

## How to resume in 60 seconds

1. `mvn test` — confirm the build is green (30 tests).
2. Check **Development phases** — all seven are complete.
3. Pick a **stretch goal** below, or add real screenshots to the report.

## Development phases

| Phase | Focus | Status |
| --- | --- | --- |
| 0 | Foundation: domain model, ScopeGuard, hash-chained ledger, rules, responses, engine | ✅ Done |
| 1 | Networking & liveness: Console server + Sensor client, JSON protocol, heartbeats | ✅ Done (PR #1) |
| 2 | Decoys & capture: FTP/Telnet decoys, login parsing, bait-file watcher, honeytokens | ✅ Done (PR #2) |
| 3 | Detection & persistence: H2 DAO layer, password-spray rule | ✅ Done (PR #3) |
| 4 | Active defence: Console→Sensor commands, tarpit, block list | ✅ Done (PR #4) |
| 5 | Specter red team: scan/spray/replay/brute strategies, campaigns, stealth, Specter UI | ✅ Done (PR #5) |
| 6 | Console dashboard (Swing): live alert feed, ledger-verify, status line | ✅ Done (PR #6) |
| 7 | Integration, testing & demo: end-to-end test, demo script, final docs | 🔨 In review (PR #7) |

## Project status — feature-complete

All seven phases are built, and the capstone **end-to-end test** proves the full chain:
Specter replays a honeytoken → decoy captures it → Sensor reports over the socket link → Console
classifies it CRITICAL → records it in the hash-chained ledger → dispatches a scoped BLOCK. The demo
is scripted in `DEMO.md`. Everything is gated by `ScopeGuard` to the lab range. **30 tests green**;
CI runs `mvn verify` on every PR and on `master`.

## Stretch goals (optional, for a future session)

- Wire real TLS: build `SSLServerSocketFactory` / `SSLSocketFactory` from the lab keystore (see
  `certs/README.md`) and pass them to `ConsoleServer` / `SensorClient` instead of the plain defaults.
- Per-entry digital signatures on ledger alerts (in addition to the hash chain).
- Cross-host correlation by sensor id (carry the sensor id on each event).
- Replace screenshot placeholders in the report with real screenshots of the two GUIs.

## Decisions log

- **Topic:** deception-based IDS ("Mirage"), two-sided (blue Console + red Specter).
- **Safety:** all offence/active-defence gated by `ScopeGuard` to a lab CIDR; non-IP sources refused.
- **"Counter-attack":** trap + slow (tarpit) + mislead + block — all wired.
- **Build:** single Maven module. Deps: Jackson, H2. Java 17 (JDK 21 toolchain). GitHub Actions CI.
- **GUIs:** Specter and the Console dashboard launch headless-safe; display logic stays in testable
  non-GUI classes.
- **Course:** Object Oriented Design and Programming-II Laboratory (ICE-2206).

## Session log

- **2026-10-13** — Scope + docs (report, proposal); Phase 0 bootstrap; course/instructors confirmed.
- **2026-10-13 (Phases 1–6)** — networking+CI (#1), decoys (#2), persistence (#3), active defence (#4),
  Specter (#5), dashboard (#6) — all merged.
- **2026-10-13 (Phase 7)** — end-to-end integration test + `DEMO.md` + final docs; 30 tests green;
  opened PR #7. Project feature-complete.

## Links

- **Proposal (submission):** https://claude.ai/code/artifact/d5969a4d-9277-473a-8c66-4d32e04716df
- **Report (detailed):** https://claude.ai/code/artifact/f578aa80-d25b-4514-8adf-f03f27562c3b
- In-repo copies: `PROPOSAL.md` (proposal), `REPORT.md` (report), `DEMO.md` (demo script)
