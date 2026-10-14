# HANDOVER — Mirage

_Last updated: 2026-10-14_

Start here each session, then read `CLAUDE.md` for conventions.

## How to resume in 60 seconds

1. `mvn test` — confirm the build is green.
2. Check **Development phases** — all seven are complete, and all four stretch goals are done.
3. The system is feature-complete and documented. Next work is polish: write the student
   **reflection** in the Conclusion, rehearse the `DEMO.md` walkthrough, and prepare slides.

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
| 7 | Integration, testing & demo: end-to-end test, demo script, final docs | ✅ Done (PR #7) |

## Project status — feature-complete

All seven phases **and all four stretch goals** are built. The capstone **end-to-end test**
proves the full chain: Specter replays a honeytoken → decoy captures it → Sensor reports over the
socket link → Console classifies it CRITICAL → records it in the hash-chained ledger → dispatches a
scoped BLOCK. The demo is scripted in `DEMO.md`. Everything is gated by `ScopeGuard` to the lab
range. **37 tests green**; CI runs `mvn verify` on every PR and on `master`.

## Stretch goals — all done

- ✅ Real TLS on the Console↔Sensor link (`SSLServerSocketFactory`/`SSLSocketFactory` from the lab
  keystore). — PR #8
- ✅ Per-entry digital signatures on ledger alerts, ECDSA, in addition to the hash chain. — PR #9
- ✅ Cross-host correlation by sensor id (events tagged with their sensor; lateral-movement rule). — PR #10
- ✅ Real screenshots of both GUIs in the report (Figures 5–7), replacing the wireframe/placeholders. — this PR

## Screenshots

Report Section 5 now shows real captures of the running build:
`docs/screenshots/{console-dashboard,specter-console,ledger-verify}.png`. Regenerate with
`mvn compile && tools/screenshots/capture.sh` (needs Xvfb + ImageMagick; harnesses in
`tools/screenshots/`). While capturing, a real UI bug was fixed: the dashboard's periodic status
refresh used to wipe the ledger-verify result after 2s — it now persists the last check.

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
- **2026-10-13 (Phase 7)** — end-to-end integration test + `DEMO.md` + final docs; opened and
  merged PR #7. Project feature-complete.
- **2026-10-13/14 (stretch goals)** — real TLS (#8), ECDSA ledger signatures (#9), cross-host
  correlation + lateral-movement rule (#10) — all merged.
- **2026-10-14 (GUI screenshots)** — captured real Dashboard + Specter screenshots headlessly
  (Xvfb + ImageMagick), demonstrated ledger tamper-detection (verified/BROKEN), rewrote Report
  Section 5 (Figures 5–7) in both the artifact and `REPORT.md`, committed the figures and the
  capture harnesses, and fixed the dashboard verify-status refresh bug. 37 tests green.

## Links

- **Proposal (submission):** https://claude.ai/code/artifact/d5969a4d-9277-473a-8c66-4d32e04716df
- **Report (detailed):** https://claude.ai/code/artifact/f578aa80-d25b-4514-8adf-f03f27562c3b
- In-repo copies: `PROPOSAL.md` (proposal), `REPORT.md` (report), `DEMO.md` (demo script)
