# HANDOVER — Mirage

_Last updated: 2026-11-08 (PR #12 merged into `master`)_

Start here each session, then read `CLAUDE.md` for conventions.

## ▶ RESUME HERE — everything is on `master`; rehearse next

**PR #12 (`feature/live-shells`) is merged into `master`** as merge commit `acfd8ee`
(2026-11-08), with all its commits kept; CI passed on `master`. The branch still exists but is no
longer needed: start any new work on a fresh branch off `master`, with a draft PR.

Every line of [`docs/stress-test/CHECKLIST.md`](docs/stress-test/CHECKLIST.md) is ticked (waves
1–3, 2026-11-07/08), and so are waves 1 and 2 of
[`docs/feature-check/CHECKLIST.md`](docs/feature-check/CHECKLIST.md) (2026-11-08), each with a
test that failed before its fix. One commit per checklist line (or tightly related group); the
commit messages explain each fix and its design choices.

- **Baseline:** `.\mvnw.cmd -B test` (PowerShell) or `./mvnw -B test`: **299 tests green**. Maven is
  not installed; always use the wrapper.
- **GUI dry run:** `tools\screenshots\dry-run.cmd` (Windows; it runs
  `tools/screenshots/LiveDemoShot.java`), **28 checks**, all pass; `docs/screenshots/live-*.png`
  were redrawn after the feature-check wave 2 fixes. How-to: `tools/screenshots/README.md`.
- **What changed for the demo** (DEMO.md is up to date):
  - "Feed fake data" is a real response now (B-09): a fed source is let in to fake files; it
    replaces the tarpit (latest wins), and HIGH auto-response feeds fake data.
  - Unblock gives a real clean slate (B-03); one honeytoken break-in is one alert and one BLOCK
    (B-04); the demo campaign records 4 alerts, not 5.
  - Rules count within time windows (B-28): brute force = 5 passwords on one account in 60 s,
    spray = one password on 3 accounts in 10 min, scan = 2 services in 60 s. Specter's brute force
    sends 8.
  - Sensor ids are `sensor-<host>-<http port>` (B-11), settable with `--sensor-id`.
  - Unknown flags and impossible ports are refused (B-32); `--lab-cidrs` also works.
  - Optional `MIRAGE_DB_PASSWORD` encrypts the database (A-07); see the README table.
- **Pre-rehearsal feature check (2026-11-08):** two agents re-tested every feature of the running
  program → [`docs/feature-check/CHECKLIST.md`](docs/feature-check/CHECKLIST.md) (details in
  `A-backend.md` / `B-gui.md`, screenshots in `evidence/`). No HIGH findings; 8 MEDIUM, 13 LOW,
  mostly about what the screens and docs tell the presenter. **Waves 1 and 2 are fixed** (plus
  FC-01 and FC-02, two flaky tests found along the way); **wave 3 (polish: FB-11 to FB-17,
  FA-02) is still open**.
- **Left to do:**
  1. (User) The real two-laptop rehearsal (`docs/TWO-LAPTOP-SETUP.md`; firewall commands run as
     admin). Add anything it finds to the feature-check checklist and fix it the same way.
  2. Optional: feature-check wave 3.
  3. The report text (`REPORT.md`, `docs/report.html`) still says 265 tests; update and republish
     it if the number matters.
  4. (User) Voice-over for the demo video. Current video is **v2** (1:48, 1080p, drawn pointer,
     part titles and cards built in): `D:\Mirage demo video v2.mp4`, on the timeline of the
     Clipchamp project "Mirage demo video v2" ready for narration. Status, cue sheet and how to redo it:
     [`docs/DEMO-VIDEO-PROGRESS.md`](docs/DEMO-VIDEO-PROGRESS.md). The self-recording demo
     driver is `tools/video/` (`run-demo-video.cmd`).
- **Not covered by either review** (unchanged): a dependency CVE scan, IPv6, and the real
  two-laptop network.

**Things to know when continuing from another account:**
- The formatted report's source is `docs/report.html` (figures from `docs/screenshots/`). If the
  report artifact (link at the bottom) can't be edited from the account you are on, publish it as
  a new artifact, passing the `screenshots/*.png` files, then update the link here and at the top
  of `REPORT.md`.

## ⚡ LIVE SHELLS & TWO-LAPTOP DEMO — built and merged into `master` (PR #12)

All phases (0–9) of the live-shells feature were built on `feature/live-shells` and merged into
`master` through PR #12 (link below) on 2026-11-08. The phase-by-phase log is **`BUILD_LOG.md`**.

- **Build/test:** Maven is **not installed** on the demo laptop. Use the committed wrapper:
  `./mvnw -B test` (PowerShell: `.\mvnw.cmd -B test`). **299 tests green.**
- **Run:** `.\mvnw.cmd -DskipTests package` → `target/mirage.jar`; `run-defender.bat` (Laptop A:
  Console + Sensor) and `run-attacker.bat` (Laptop B: Specter + counter-watch). Set-up:
  `docs/TWO-LAPTOP-SETUP.md`. Script: `DEMO.md`.
- **GUI dry run:** `tools/screenshots/LiveDemoShot.java` clicks through the whole demo (28 checks,
  all pass) and regenerates `docs/screenshots/live-*.png`. How-to: `tools/screenshots/README.md`.
- **Still to do:** the stress-test checklist and feature-check waves 1–2 are done and merged; next
  is the user's real two-laptop rehearsal (firewall commands are in the setup doc, to run as
  admin).
- **Report artifact:** a fresh formatted report with all 14 figures (Figures 1–4 redrawn as
  diagrams, 5–14 screenshots) was published from this account: link below. Its source is
  `docs/report.html` (figures from `docs/screenshots/`), so any account can republish it (see
  RESUME HERE above for how).
- **What it adds:** terminal-style "process shells" on both sides that stream each step live (and
  split into one pane per thread for concurrent runs), a decoy that lets a trap credential "in" to a
  fake page, an operator-gated (manual-by-default) response flow, a counter-watch listener on the
  attacker laptop, and a one-jar launcher so the two sides can run on two laptops over a private LAN.
- **Safety unchanged:** everything still routes through `ScopeGuard`; the configurable lab range is
  additionally forced to be private/loopback and ≤ /16 by `ScopeGuard.requireLabRange`.

## How to resume the ORIGINAL (merged) project in 60 seconds

1. `./mvnw test` — confirm the build is green (use the wrapper; `mvn` is not installed).
2. Check **Development phases** — all seven are complete, and all four stretch goals are done.
3. On `master`, the system is feature-complete and documented, including the live-shells feature
   above (merged from `feature/live-shells` through PR #12).

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
- **2026-11-07 (report + stress test)** — published a fresh formatted report (`docs/report.html`,
  14 figures; Figures 1–4 redrawn as diagrams). Ran two parallel stress-test agents (security;
  robustness) → `docs/stress-test/A-security.md`, `B-robustness.md` and the ordered fix list
  `CHECKLIST.md`. The user then trimmed the security findings and renumbered them (A-01 to A-09);
  references were reconciled. No source changes yet; fixing is the next job, from another account.
- **2026-11-08 (stress-test fixes, feature check, merge)** — fixed every stress-test line (265
  tests). Two agents then re-tested every feature → `docs/feature-check/`; fixed its waves 1 and 2
  plus two flaky tests (299 tests, dry run 28/28, new `tools\screenshots\dry-run.cmd`), redrew the
  live figures, republished the report artifact (v3). Merged `master` into the branch (keeping
  the branch's CLAUDE.md), then merged PR #12 into `master` (`acfd8ee`); CI green.

## Links

- **PR #12 (live shells + stress-test and feature-check fixes, merged into `master`):** https://github.com/NoboIshraque/cybersecurity/pull/12
- **Feature-check fix list:** `docs/feature-check/CHECKLIST.md`
- **Stress-test fix list:** `docs/stress-test/CHECKLIST.md`
- **Proposal (submission):** https://claude.ai/code/artifact/d5969a4d-9277-473a-8c66-4d32e04716df
  (first account; can't be edited from another account; source `PROPOSAL.md`)
- **Report (detailed, current):** https://claude.ai/artifact/7Hf6qAtuzF3i9TdU72jJgg (published from
  the second account; source `docs/report.html`, republish from a new account as a new artifact)
- Report (original, first account): https://claude.ai/code/artifact/f578aa80-d25b-4514-8adf-f03f27562c3b
- In-repo copies: `PROPOSAL.md` (proposal), `REPORT.md` (report), `DEMO.md` (demo script)
