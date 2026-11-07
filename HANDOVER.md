# HANDOVER — Mirage

_Last updated: 2026-11-07_

Start here each session, then read `CLAUDE.md` for conventions.

## ⚡ LIVE SHELLS & TWO-LAPTOP DEMO — built, in draft PR (branch `feature/live-shells`)

All phases (0–9) of the live-shells feature are done on `feature/live-shells`, pushed, with a
**draft PR** to `master` (link below). Merge it once the real two-laptop rehearsal has been done.
The phase-by-phase log is **`BUILD_LOG.md`**.

- **Build/test:** Maven is **not installed** on the demo laptop. Use the committed wrapper:
  `./mvnw -B test` (PowerShell: `.\mvnw.cmd -B test`). **98 tests green.**
- **Run:** `.\mvnw.cmd -DskipTests package` → `target/mirage.jar`; `run-defender.bat` (Laptop A:
  Console + Sensor) and `run-attacker.bat` (Laptop B: Specter + counter-watch). Set-up:
  `docs/TWO-LAPTOP-SETUP.md`. Script: `DEMO.md`.
- **GUI dry run:** `tools/screenshots/LiveDemoShot.java` clicks through the whole demo (27 checks,
  all pass) and regenerates `docs/screenshots/live-*.png`. How-to: `tools/screenshots/README.md`.
- **Still to do (user):** the real two-laptop rehearsal (firewall commands are in the setup doc
  for you to run as admin); then mark the PR ready and merge.
- **Report artifact:** the formatted report artifact (link below) belongs to the account this
  project was handed over from and cannot be edited from the current one. `REPORT.md` has the
  new §5.5 (Figures 9–14); copy it into the artifact from that account, or publish a fresh one.
- **What it adds:** terminal-style "process shells" on both sides that stream each step live (and
  split into one pane per thread for concurrent runs), a decoy that lets a trap credential "in" to a
  fake page, an operator-gated (manual-by-default) response flow, a counter-watch listener on the
  attacker laptop, and a one-jar launcher so the two sides can run on two laptops over a private LAN.
- **Safety unchanged:** everything still routes through `ScopeGuard`; the configurable lab range is
  additionally forced to be private/loopback and ≤ /16 by `ScopeGuard.requireLabRange`.

## How to resume the ORIGINAL (merged) project in 60 seconds

1. `./mvnw test` — confirm the build is green (use the wrapper; `mvn` is not installed).
2. Check **Development phases** — all seven are complete, and all four stretch goals are done.
3. On `master`, the system is feature-complete and documented. The live-shells feature above is the
   draft PR on `feature/live-shells`.

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
range. **48 tests green**; CI runs `mvn verify` on every PR and on `master`.

## Stretch goals — all done

- ✅ Real TLS on the Console↔Sensor link (`SSLServerSocketFactory`/`SSLSocketFactory` from the lab
  keystore). — PR #9
- ✅ Per-entry digital signatures on ledger alerts, ECDSA, in addition to the hash chain. — PR #8
- ✅ Cross-host correlation by sensor id (events tagged with their sensor; lateral-movement rule). — PR #10
- ✅ Full Console dashboard built — the network map, attacker dossier, session replay, manual-override
  response panel, and red-highlighted broken ledger link — shown in the report with real screenshots
  (Figures 5–8). — this PR

## Console dashboard features (built)

The dashboard (`console.ui.Dashboard`) now has: a **network map** (`NetworkMapPanel`, nodes grey→red
by severity), the **live alert feed**, and a per-source detail panel (`AttackerDetailPanel`) with an
**attacker dossier** (`AttackerDossier` from `AttackerSnapshot`), a **session replay** of the raw
interactions, and a **responses / manual-override** tab (`ResponseController`, every command
`ScopeGuard`-checked). The engine publishes immutable snapshots to an `AttackerDirectory` (no mutable
state crosses threads). `AlertLedger.firstBrokenIndex()` drives the red broken-row highlight. New
non-GUI logic is unit-tested (`AttackerDossierTest`, `AttackerDirectoryTest`, `ResponseControllerTest`,
`LedgerBrokenIndexTest`). The responses tab also has a **counter-attack** button
(`console.CounterOffensive` + `MirrorCounterStrategy`): it maps the source's detected MITRE
technique to the matching Specter campaign and launches it back at the source through the Specter
`Attacker` — which calls `ScopeGuard.require` first, so a counter can only ever reach an in-range
source (tested in `MirrorCounterStrategyTest`, `CounterOffensiveTest`). There is also a
**Simultaneous counter** button: `CounterOffensive.counterAll` → `Attacker.launchConcurrent` runs
all four counter methods at once, each on its own pool thread (a bounded set, not a flood) — the
concurrency is proven in `CounterOffensiveTest.counterAllRunsEveryMethodAtTheSameTime`.

## Screenshots

Report Section 5 shows real captures of the running build:
`docs/screenshots/{console-dashboard,specter-console,detail-replay-responses,ledger-broken}.png`.
Regenerate with `mvn compile && tools/screenshots/capture.sh` (needs Xvfb + ImageMagick; harnesses in
`tools/screenshots/`).

## Decisions log

- **Live shells (2026-11-07):** Specter "gets in" by decoy deception (a trap credential opens a
  canned fake admin page); responses are operator-gated with an auto-response switch (off with the
  GUI, on headless); Specter notices counters passively (`CounterWatch`, accept-and-log only);
  threads shown both as a tagged stream and as split panes. Lab range configurable but forced
  private/loopback and ≤ /16. Each rule alerts once per source (again on higher severity or after
  an Unblock). A blocked decoy rejects with a TCP reset (so "blocked" ≠ "no reply").

- **Topic:** deception-based IDS ("Mirage"), two-sided (blue Console + red Specter).
- **Safety:** all offence/active-defence gated by `ScopeGuard` to a lab CIDR; non-IP sources refused.
- **"Counter-attack":** trap + slow (tarpit) + mislead (fake data) + block, plus an
  operator-triggered **counter-offensive** (`CounterOffensive` + `MirrorCounterStrategy`) that
  mirrors the attacker's own technique back at an in-range source via Specter — all `ScopeGuard`-gated,
  so it can never reach outside the lab. Not a hack-back.
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
- **2026-10-13/14 (stretch goals)** — ECDSA ledger signatures (#8), real TLS (#9), cross-host
  correlation + lateral-movement rule (#10) — all merged.
- **2026-10-14 (GUI screenshots)** — captured real Dashboard + Specter screenshots headlessly
  (Xvfb + ImageMagick), demonstrated ledger tamper-detection (verified/BROKEN), rewrote Report
  Section 5 in both the artifact and `REPORT.md`, committed the figures and the capture harnesses,
  and fixed the dashboard verify-status refresh bug.
- **2026-10-14 (dashboard features)** — built the GUI features Section 5 had described as future
  work: network map, attacker dossier, session replay, manual-override response panel, and the
  red-highlighted broken ledger link. Added `AttackerDirectory`/`AttackerSnapshot`,
  `AttackerDossier`, `ResponseController`, `AlertLedger.firstBrokenIndex()`, `NetworkMapPanel`,
  `AttackerDetailPanel` (+ 4 new test classes); rewrote Report Section 5 (Figures 5–8) to match.
  43 tests green.
- **2026-10-14 (counter-offensive)** — built the lab-bounded "counter-attack" (`CounterStrategy`,
  `MirrorCounterStrategy`, `CounterOffensive`): on the operator's command it mirrors the attacker's
  own detected technique back at an in-range source via Specter, `ScopeGuard`-gated (external IPs
  refused). Wired a Counter-attack button into the dashboard responses tab; +2 test classes; updated
  CLAUDE.md's hard rule and Report §5.3 (Figure 7). 46 tests green.
- **2026-10-14 (simultaneous counter)** — added `Attacker.launchConcurrent` and
  `CounterOffensive.counterAll`: a Simultaneous-counter button runs all four counter methods at once,
  each on its own thread (bounded, `ScopeGuard`-gated), showcasing multithreading. Added the
  concurrency proof test + Figure 7 now shows the `COUNTER-ALL` line. 48 tests green.

- **2026-11-07 (live shells, Phases 0–9)** — Maven wrapper; configurable lab range +
  `requireLabRange`; `TraceBus` trace plumbing + Sensor ACK; `ShellModel`/`ShellWindow`; pooled
  decoys + decoy-access deception; Specter shells + `CounterWatch`; Console `ThreatPopup`,
  `OperationShells`, auto-response switch, pipeline shell; fat jar + `Launcher` + run scripts +
  two-laptop guide; automated GUI dry run (27/27) + 13 figures; alert de-duplication and the
  reset-vs-silent-close fix; DEMO/README/CLAUDE/REPORT §5.5 updated; draft PR opened. 98 tests green.

## Links

- **Proposal (submission):** https://claude.ai/code/artifact/d5969a4d-9277-473a-8c66-4d32e04716df
- **Report (detailed):** https://claude.ai/code/artifact/f578aa80-d25b-4514-8adf-f03f27562c3b
- In-repo copies: `PROPOSAL.md` (proposal), `REPORT.md` (report), `DEMO.md` (demo script)
