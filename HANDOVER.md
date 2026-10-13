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
| 5 | Specter red team: scan/spray/replay/brute strategies, campaigns, stealth, Specter UI | 🔨 In review (PR #5) |
| 6 | Console dashboard (Swing): network map, alert feed, dossier, ledger-verify view | ⬜ Next |
| 7 | Integration, testing & demo: end-to-end lab run, demo script, report screenshots | ⬜ Planned |

## Done so far

- **Phase 0–4** — foundation; networking + heartbeats + CI; decoys & capture + honeytokens; H2
  persistence + password-spray rule; active defence (Console→Sensor commands, tarpit, block list).
- **Phase 5** (in review) — Specter red team: `PortScanAttack`, `PasswordSprayAttack`,
  `HoneytokenReplayAttack`, `BruteForceAttack` (now proper Base64 Basic-auth); a `Campaign` bundler;
  `Attacker` runs a campaign with a stealth delay, scope-guarded on the caller thread; a `SpecterConsole`
  Swing panel (target + campaign + stealth slider + live log), launched headless-safe from `SpecterMain`.
  28 tests green (incl. an end-to-end spray against a loopback decoy).

## Current / next phase — Phase 6: Console dashboard (Swing)

**Goal.** A live blue-team dashboard, the counterpart to the Specter console.

- [ ] A `Dashboard` JFrame that subscribes to the `AlertBus` and shows alerts in a live table
      (time, source, classification, ATT&CK, severity), newest first, colour-coded by severity.
- [ ] A "verify ledger" button that runs `AlertLedger.verify()` and shows the result.
- [ ] A simple sensor/connection status line (from `SensorRegistry` / `ConsoleServer.connectionCount`).
- [ ] Wire the dashboard into `ConsoleMain` (headless-safe, like Specter).
- [ ] Keep display logic in a testable view-model where practical; GUI itself just compiles.

(Merge PR #5 first, then branch Phase 6 off an up-to-date `master`.)

## Decisions log

- **Topic:** deception-based IDS ("Mirage"), two-sided (blue Console + red Specter).
- **Safety:** all offence/active-defence gated by `ScopeGuard` to a lab CIDR; non-IP sources refused.
  `Attacker` checks scope synchronously on the caller thread before launching.
- **"Counter-attack":** trap + slow (tarpit) + mislead + block — all wired (Phase 4).
- **Build:** single Maven module. Deps: Jackson, H2. Java 17. GitHub Actions CI.
- **GUIs** (Specter now, Console dashboard next) are launched headless-safe; logic stays in testable
  non-GUI classes.
- **Course:** Object Oriented Design and Programming-II Laboratory (ICE-2206).

## Session log

- **2026-10-13** — Scope + docs; Phase 0 bootstrap; course/instructors confirmed.
- **2026-10-13 (Phases 1–4)** — networking+CI (PR #1), decoys (PR #2), persistence (PR #3),
  active defence (PR #4) — all merged.
- **2026-10-13 (Phase 5)** — Specter red team (strategies, campaign, stealth, Swing console);
  28 tests green; opened PR #5.

## Links

- **Proposal (submission):** https://claude.ai/code/artifact/d5969a4d-9277-473a-8c66-4d32e04716df
- **Report (detailed):** https://claude.ai/code/artifact/f578aa80-d25b-4514-8adf-f03f27562c3b
- In-repo copies: `PROPOSAL.md` (proposal), `REPORT.md` (report)
