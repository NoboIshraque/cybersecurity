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
| 0 | Foundation: domain model, ScopeGuard, hash-chained ledger, rules, responses, engine | ✅ Done |
| 1 | Networking & liveness: Console server + Sensor client, JSON protocol, heartbeats | ✅ Done (PR #1) |
| 2 | Decoys & capture: FTP/Telnet decoys, login parsing, bait-file watcher, honeytokens | ✅ Done (PR #2) |
| 3 | Detection & persistence: H2 DAO layer, password-spray rule | ✅ Done (PR #3) |
| 4 | Active defence: Console→Sensor commands, tarpit, block list | ✅ Done (PR #4) |
| 5 | Specter red team: scan/spray/replay/brute strategies, campaigns, stealth, Specter UI | ✅ Done (PR #5) |
| 6 | Console dashboard (Swing): live alert feed, ledger-verify, status line | 🔨 In review (PR #6) |
| 7 | Integration, testing & demo: end-to-end test, demo script, final docs | ⬜ Next |

## Done so far

- **Phase 0–5** — foundation; networking + CI; decoys & capture + honeytokens; H2 persistence +
  password-spray; active defence (Console→Sensor commands, tarpit, block); Specter red team.
- **Phase 6** (in review) — the blue-team `Dashboard` (Swing): an `AlertTableModel` (testable
  view-model, newest-first), a live severity-coloured alert table, a connection/ledger status line,
  and a one-click "Verify ledger" button. Wired into `ConsoleMain` headless-safe. 29 tests green.

## Current / next phase — Phase 7: Integration, testing & demo

**Goal.** Prove the whole system works end to end and make it easy to demonstrate.

- [ ] An end-to-end integration test: Specter → decoy → Sensor → Console → alert + dispatched BLOCK.
- [ ] `DEMO.md`: the projector demo script (start Console, start Sensor, run Specter campaigns,
      watch detection → classification → active defence, show the ledger intact).
- [ ] Final docs pass: README run instructions for both GUIs; confirm REPORT/PROPOSAL still match.
- [ ] Mark the build "feature-complete" for the lab; note stretch goals (TLS keystore wiring,
      per-entry signatures, cross-host-by-sensor correlation).

(Merge PR #6 first, then branch Phase 7 off an up-to-date `master`.)

## Decisions log

- **Topic:** deception-based IDS ("Mirage"), two-sided (blue Console + red Specter).
- **Safety:** all offence/active-defence gated by `ScopeGuard` to a lab CIDR; non-IP sources refused.
- **"Counter-attack":** trap + slow (tarpit) + mislead + block — all wired (Phase 4).
- **Build:** single Maven module. Deps: Jackson, H2. Java 17. GitHub Actions CI.
- **GUIs:** Specter and the Console dashboard are launched headless-safe; display logic stays in
  testable non-GUI classes (`AlertTableModel`).
- **Course:** Object Oriented Design and Programming-II Laboratory (ICE-2206).

## Session log

- **2026-10-13** — Scope + docs; Phase 0 bootstrap; course/instructors confirmed.
- **2026-10-13 (Phases 1–5)** — networking+CI (#1), decoys (#2), persistence (#3), active defence
  (#4), Specter (#5) — all merged.
- **2026-10-13 (Phase 6)** — Console dashboard (Swing) with live alert feed + ledger-verify;
  29 tests green; opened PR #6.

## Links

- **Proposal (submission):** https://claude.ai/code/artifact/d5969a4d-9277-473a-8c66-4d32e04716df
- **Report (detailed):** https://claude.ai/code/artifact/f578aa80-d25b-4514-8adf-f03f27562c3b
- In-repo copies: `PROPOSAL.md` (proposal), `REPORT.md` (report)
