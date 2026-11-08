# B — GUI, operator and demo feature check

- **Branch/commit:** `feature/live-shells` @ `b6d7f23` (worktree checked out detached at that commit; it had been cut from `master`).
- **Date:** 2026-11-08 · **JDK:** 25.0.1 (Oracle) · Windows 11, real desktop, default LAF.
- **What was run** (all evidence below is under the scratch folder
  `C:\Users\noboi\AppData\Local\Temp\claude\073c8037ca9f2332\a130b79a-095f-4851-9bae-0490cd24a971\scratchpad\agentB\`, written `<scratch>` below):
  - `mvnw -B -DskipTests package` (+ `dependency:build-classpath`) — OK.
  - `tools/screenshots/LiveDemoShot.java` twice (`dry1/`, `dry2/`), per its README (with one fix, see FB-05).
  - Own click-through harnesses modelled on LiveDemoShot (`harness/BHarness.java`, `BHarness2.java`, `BHarness3.java`, `TamperHarness.java`) driving the real Console / Sensor / Specter on the default ports; logs in `h1/`, `h2/`, `h3/`, `tamper/`.
  - Launcher roles as real processes (`launcher-test.ps1`, `launcher-test2.ps1`, `winclose.ps1`): window titles read with Win32 `EnumWindows`, windows closed with `WM_CLOSE` (what the title-bar X does). Logs in `launcher/`, `launcher2/`.
  - DEMO.md walked step by step on one machine (steps 0–7 + bait file) inside the harnesses.

## Summary

32 features checked: **23 PASS, 8 PARTIAL, 0 FAIL, 1 NOT TESTED.** 19 findings: **0 HIGH, 8 MEDIUM, 11 LOW.**
The dry run is **28/28 on both runs** (26 s each, no flakiness). No exception from the application in any log (the only stack trace is my own harness's, see S-note in FB-16). The safety boundary held everywhere: out-of-range Specter targets, an out-of-range *source* (Block, Counter, Simultaneous counter) are all refused with a clear REFUSED banner.

## Feature results

| # | Feature | How tested | Result | Findings |
|---|---|---|---|---|
| 1 | GUI dry run (LiveDemoShot) | README steps, twice; read all 13 `live-*.png` | PASS 28/28 ×2 (26 s each). Figures 13/9 show only 1–2 of 4–5 thread panes | FB-03, FB-05, FB-10 |
| 2 | Dashboard opens, title, status line | harness + Win32 titles | PASS (`Mirage Console — live`; status `Console at 127.0.0.1:8443 | Alerts | Sensors connected | Auto-response | Ledger`) | FB-18 (wording) |
| 3 | Live alert feed / table, newest first, severity colours | harness, screenshots | PASS (columns truncated at default size) | FB-18 |
| 4 | Network map grey→red, click to select | harness (reflection on nodes + synthetic click) | PARTIAL: MEDIUM→CRITICAL colouring and click work; grey node only appears after some other alert | FB-14 |
| 5 | Detail panel: dossier, session replay, follows selection live | row select, map click, tab screenshots | PASS | — |
| 6 | Responses tab / manual override (buttons disabled until a source is selected) | harness | PASS | — |
| 7 | Verify ledger → verified | harness | PASS (state goes stale afterwards) | FB-15 |
| 8 | Ledger BROKEN + red row after tampering | seed → H2 `UPDATE alert` offline → restart → Verify, twice | PARTIAL: `BROKEN (entry 1)` yes; red row never; feed empty after restart | FB-02 |
| 9 | Auto-response OFF with dashboard, ON switch | harness | PASS (status `ON`, popup NEUTRALISED in ~360 ms, pipeline shows `auto-response: block`) | — |
| 10 | ThreatPopup escalation PROBE→UNDER ATTACK→COMPROMISED→NEUTRALISED, colours, reason line | harness | PASS | FB-09 (doc) |
| 11 | Popup buttons (Tarpit, Feed fake data, Block, Counter-attack, Simultaneous counter, Inspect) | dry run + harness | PASS | — |
| 12 | Same operations from dashboard Responses tab incl. Unblock | harness | PASS (op shell, `[op-tarpit]` etc., ScopeGuard line, Sensor ACK, response log line) | FB-10, FB-13 |
| 13 | Tarpit effect on Specter | replay / recon / spray after tarpit | PASS (STALLED in 1.7 s; recon still `3 OPEN`) | — |
| 14 | Feed fake data effect | brute + spray after FEED_FAKE | PARTIAL: ACCESS GRANTED on first guess, but the fake files are never visible | FB-06 |
| 15 | Block effect | replay / brute after block | PASS (`connection reset (blocked)` → CONNECTION BLOCKED; recon still sees ports open) | — |
| 16 | Unblock = clean slate | Unblock, then brute + replay | PASS (fake data and tarpit cleared: brute NO ACCESS, replay ACCESS GRANTED; rules re-alert: 4 → 6 alerts; popup back to COMPROMISED) | — |
| 17 | Counter-attack / Simultaneous counter reach counter-watch, in-range only | dry run + harness; out-of-range source via test sensor | PASS (INCOMING banner, NO REPLY; `192.168.77.7` refused for Block and counters) | FB-03, FB-07 |
| 18 | Console pipeline shell, live and in order | opened before attacks | PASS | — |
| 19 | Late shell gets backlog (TraceBacklog) | second pipeline shell opened after the campaign | PASS (scan < spray < brute < honeytoken order) | — |
| 20 | Split panes for concurrent runs | Full campaign "all at once", Simultaneous counter | PARTIAL: one pane per thread is created, but only 1–2 are on screen | FB-03, FB-07 |
| 21 | Shell buffer cap under load | 3,000-event flood + 40 sources via a test sensor | PASS (10,045 lines produced, ≤ 5,000 on screen, no exception) | — |
| 22 | Closing shells / popups | closed Specter shell mid-run, late pipeline shell, popup (hides, re-shows on next escalation) | PASS | FB-19 |
| 23 | Specter campaigns (recon, brute, spray, honeytoken, full, all-at-once) | harness | PASS | — |
| 24 | Specter outcomes honest (ACCEPTED/REJECTED/DROPPED/NO_REPLY/STALLED) | tarpit, block, counter-watch, closed port, silent host, Sensor down | PARTIAL: a closed port / Sensor down / unreachable host is reported **CONNECTION BLOCKED** | FB-01 |
| 25 | Out-of-range target refused; bad port | 8.8.8.8, 192.168.1.1, localhost, "", "not a host", abc, 0, 70000 | PASS (refused / `invalid port` in the log area, no shell). Wording off for non-IPs | FB-12 |
| 26 | Stealth slider | brute 1000 ms, spray 2000 ms | PARTIAL: the attempts are not slowed | FB-11 |
| 27 | Launcher roles + titles (`java -jar … defender/attacker`, `run-*.bat`) | real processes, Win32 titles | PASS (defender: `Mirage Console — live`; attacker: `Specter — attack simulator (lab range only)` + `Specter — counter-watch (incoming)`; both .bat files the same) | FB-08 |
| 28 | Close window → clean exit | WM_CLOSE on dashboard / Specter (jar and .bat) | PASS (JVM gone in < 4 s; restart reopens the DB cleanly) | FB-19 |
| 29 | Second defender while one runs | same folder and other folder | PASS (clear one-line message, exit 1, no dashboard) | — |
| 30 | DEMO.md walk-through (steps 0–7, bait file) | harnesses | PARTIAL: flow works; 6 wording / claim differences | FB-02, FB-06, FB-09, FB-18 |
| 31 | Robustness: many alerts, resize/small screen, Sensor disconnect/reconnect | 40 sources + flood; 800/1024 widths; Sensor process killed and restarted | PARTIAL: stays responsive (max EDT lag 1.46 s), Sensor count 1→0 in 1.5 s and back; layout breaks below ~1100 px | FB-04, FB-16 |
| 32 | Dark/light rendering | screenshots of all windows | NOT TESTED as a theme switch (Swing Metal ignores the Windows theme); in the fixed theme, pane titles in split view are dark grey on black | FB-13 |

## Findings

### FB-01 · MEDIUM · Specter says "CONNECTION BLOCKED" when nothing blocked it (closed port, Sensor down, unreachable host — after a 21 s hang)
- **Where:** `specter/BruteForceAttack.java` `bannerFor` (DROPPED → "CONNECTION BLOCKED" ignores `connectFailed()`); `ProbeReply.probe` uses `new Socket(host, port)` with no connect timeout.
- **Steps:** (a) Specter → Honeytoken replay / Brute force at `127.0.0.1:8099` (nothing listening). (b) Kill the Sensor, then Honeytoken replay at `127.0.0.1:8081`. (c) Honeytoken replay at `10.10.0.5:8081` (in the default lab range, no host there).
- **Expected / Actual:** expected a "connection failed / nothing answering" outcome. Actual: line `replay -> connection failed` but banner **CONNECTION BLOCKED** every time; in (c) after **21.3 s** of no output. With a misconfigured Laptop A firewall on the rehearsal, the attacker screen will claim the defender blocked it, after a long silence per strategy.
- **Evidence:** [`h1/shots/b-specter-closed-port.png`](evidence/h1/shots/b-specter-closed-port.png), [`h2/shots/r-closed-port-brute.png`](evidence/h2/shots/r-closed-port-brute.png), [`h3/shots/t-silent-target.png`](evidence/h3/shots/t-silent-target.png); `h2\run.log` R4/R10 (`Specter while the sensor is down -> [CONNECTION BLOCKED]`), `h3\run.log` T1 (`banner after 21317 ms -> [CONNECTION BLOCKED]`). Reproduced in 3 runs.
- **Suggested fix:** banner `CONNECTION FAILED — NOTHING LISTENING` (or `UNREACHABLE`) when `connectFailed()`; connect with `socket.connect(addr, 3000)` like `PortScanAttack` does.

### FB-02 · MEDIUM · Ledger tamper: no red row in the live app; stored alerts aren't shown after a restart
- **Where:** `ConsoleMain.start` (loads the chain into `AlertLedger` but not into the dashboard), `Dashboard.verifyLedger`; DEMO step 7, REPORT §5.4, and the Console's own `[warn] … the dashboard marks it in red`.
- **Steps:** run the Console, raise 3 alerts, close; `UPDATE alert SET severity='LOW'` on the 2nd row (H2 file, Console stopped); restart; click **Verify ledger**. Done twice.
- **Expected / Actual:** expected `BROKEN (entry 1)` and that row red. Actual: stdout says `chain BROKEN at entry #1`, but the dashboard opens with `Alerts: 0` and `Ledger: —`, the feed is empty, and after Verify the status reads `BROKEN (entry 1)` with **0 red rows**, since the row isn't in the table. The red row can only be seen in the synthetic `DashboardShot broken` figure. A running Console can't be tampered with (the DB is locked), so the demo can't show the red row at all.
- **Evidence:** `tamper/verify.log` (`table rows on start = 0`, `red (broken) rows in the table = 0`), [`tamper/shots/b-ledger-after-tamper.png`](evidence/tamper/shots/b-ledger-after-tamper.png).
- **Suggested fix:** seed the alert table from `ledger.entries()` at start-up and run the check once at start, so a broken chain shows immediately in red; or reword DEMO step 7 and the `[warn]` line.

### FB-03 · MEDIUM · Split view shows only 1–2 of the 4–5 thread panes; the rest are off-screen
- **Where:** `ui/shell/ShellWindow` (`splitContainer` = `GridLayout(1,0)` inside a horizontal `JScrollPane`); Specter "All at once", the counter shell; REPORT Figure 13 and DEMO step 3/5 ("one pane per thread, all at once").
- **Steps:** Full campaign with *All at once*; Simultaneous counter.
- **Expected / Actual:** expected all thread panes side by side. Actual: at the default 860–880 px width, 4 panes give 1 fully visible pane plus half of the next, and 5 panes give 1 plus a sliver. The others need horizontal scrolling. The thread strip also clips (`threads: 7` with 4 labels shown). The published figures `live-specter-concurrent.png` and `live-op-shell-counter-split.png` show the same thing, so the figure doesn't show what its caption says.
- **Evidence:** [`dry1/shots/live-op-shell-counter-split.png`](evidence/dry1/shots/live-op-shell-counter-split.png), [`dry1/shots/live-specter-concurrent.png`](evidence/dry1/shots/live-specter-concurrent.png), [`h1/shots/b-counter-split.png`](evidence/h1/shots/b-counter-split.png); `h1\run.log`: `panes=4 visible-panes=1`, `panes=5 visible=1`.
- **Suggested fix:** make the split container track the viewport width (`Scrollable.getScrollableTracksViewportWidth() = true`) or use a 2×N grid; let the strip wrap.

### FB-04 · MEDIUM · Dashboard and popup layout break below ~1100 px wide (projector resolutions)
- **Where:** `Dashboard.buildUi` (fixed `1120×680`, divider pinned at 560), `AttackerDetailPanel` (long counter label and button rows set a large minimum width), `ThreatPopup` button row.
- **Steps:** resize the dashboard to 1024×600/700 and 800×500; the popup to 640×420.
- **Expected / Actual:** at 1024 px the alert feed collapses to ~240 px (every column truncated) and the status line cuts off at `Ledger:`, which hides the value DEMO step 7 tells the presenter to read. At 800 px the **Unblock** button and the status tail are gone. At 640 px the popup loses **Inspect in dashboard**, which DEMO step 6 uses. A 1024×768 projector or a 125 % scaled laptop screen hits this.
- **Evidence:** [`h2/shots/r-dashboard-1024x700-fresh.png`](evidence/h2/shots/r-dashboard-1024x700-fresh.png), [`h1/shots/b-dashboard-1024x600.png`](evidence/h1/shots/b-dashboard-1024x600.png), [`h1/shots/b-dashboard-800x500.png`](evidence/h1/shots/b-dashboard-800x500.png), [`h2/shots/r-popup-640x420.png`](evidence/h2/shots/r-popup-640x420.png).
- **Suggested fix:** wrap the long label, use a wrapping layout for the button rows, put the ledger state on its own label, and size windows from the screen size.

### FB-05 · MEDIUM · The dry-run instructions in `tools/screenshots/README.md` don't work on Windows
- **Where:** `tools/screenshots/README.md`, "Live-shells dry run" block ("Windows (PowerShell or Git Bash)").
- **Steps:** run the four lines verbatim in Git Bash from the repo root.
- **Expected / Actual:** in Git Bash `$PWD` is `/e/cybersecurity/...`, so `CP="$PWD/target/classes;…"` hands `javac` a path it can't resolve: **17 compile errors** (`package com.mirage.console does not exist`). `$OLDPWD/docs/screenshots` has the same problem. The block is bash syntax (`mkdir -p`, `&&`, `$(cat …)`), so it can't run in Windows PowerShell 5.1 either. With `$(pwd -W)` instead of `$PWD` it works (that's how I ran 28/28). DEMO.md points presenters at this dry run as the rehearsal check.
- **Evidence:** `readme-verbatim.sh` and its output (`CP starts: /e/cybersecurity/...`, `17 errors`, `javac exit=1`); `dryrun.sh` (working variant).
- **Suggested fix:** use `$(pwd -W)` (or `cygpath -w`) in the Git Bash lines and add a real PowerShell block; better still, a `tools/screenshots/dry-run.cmd`.

### FB-06 · MEDIUM · DEMO step 4: the "fake file manager" is never visible to the audience
- **Where:** DEMO.md step 4 ("a fake file manager showing `passwords.csv`, `vpn.key` and `config/backup.cfg`"); `ProbeReply.read` keeps only the first line; `BruteForceAttack.describe` prints `200 OK  ACCESS GRANTED`.
- **Steps:** Feed fake data, then Brute force.
- **Expected / Actual:** expected the file names on Specter's screen. Actual: `try admin:123456 -> 200 OK  ACCESS GRANTED`, `valid credential found`, banner. No file names anywhere in either GUI (the decoy does serve them in the HTML body). The honeytoken replay's "admin page" is likewise never shown.
- **Evidence:** `h1/run.log` ("brute-after-fake shell", `RESULT FAIL S13 fake file manager names …`).
- **Suggested fix:** have Specter print the `<h2>` titles (or the `<li>` items) of a 200 page as `ls`-style lines, or reword DEMO step 4.

### FB-07 · MEDIUM · Turning on split view after lines arrived shows an empty pane; the counter shell loses its earlier lines
- **Where:** `ShellWindow.switchView` / `appendToThreadPane` (only new lines go to thread panes; there is no replay of `model` history).
- **Steps:** (a) finish a Honeytoken replay, tick **Split threads**; (b) Counter-attack, then Simultaneous counter (which switches the same counter shell to split).
- **Expected / Actual:** (a) a blank black window (0 panes) although `lines: 6`. (b) the single counter's `op-mirror-replay` lines are no longer visible once the shell splits; only the strip remembers them.
- **Evidence:** [`h1/shots/b-split-after-the-fact.png`](evidence/h1/shots/b-split-after-the-fact.png), [`h1/shots/b-counter-split.png`](evidence/h1/shots/b-counter-split.png); `h1\run.log` (`text panes=0`).
- **Suggested fix:** when split is enabled, rebuild the thread panes from the lines already received (keep a bounded per-thread history in `ShellModel`).

### FB-08 · MEDIUM · A refused start-up from a double-clicked `run-*.bat` vanishes; attacker/sensor refusals exit 0
- **Where:** `run-defender.bat`, `run-attacker.bat` (no `pause` on failure); `SpecterMain.main` / `SensorMain` return normally after "Refusing to start".
- **Steps:** start a second defender; start the attacker with `--lab-cidr=8.8.8.0/24` or an unknown flag; check `%ERRORLEVEL%`.
- **Expected / Actual:** the defender prints a clear one-liner and exits 1 (good), but a double-clicked .bat window closes at once, so the message the TWO-LAPTOP-SETUP troubleshooting table relies on is never seen. `attacker` and `sensor` refusals exit **0**, so scripts can't tell them from a normal start.
- **Evidence:** `launcher2/` runs: `attacker public range exit=0`, `attacker unknown flag exit=0`, `sensor unknown flag exit=0`, `defender … exit=1`.
- **Suggested fix:** `if errorlevel 1 pause` at the end of both .bat files; `System.exit(1)` on a refused start in SpecterMain and SensorMain.

### FB-09 · LOW · DEMO step 3 / REPORT Figure 10 still promise a second "Intruder inside decoy" CRITICAL alert
- **Where:** DEMO.md step 3 ("*Stolen credentials used (honeytoken)* and *Intruder inside decoy (honeypot session)*, both CRITICAL"); REPORT Figure 10 caption.
- **Actual:** since B-04 only `Stolen credentials used (honeytoken)` is alerted; the shell says `DecoyAccessRule also matches: T1078 CRITICAL is the same finding — not alerted twice`. The table has 4 rows (scan, spray, brute, honeytoken), which matches HANDOVER's "4 alerts, not 5".
- **Evidence:** `h1/run.log` (alert table rows), `dry1\shots\live-threat-popup-compromised.png`.
- **Suggested fix:** reword both to "one CRITICAL finding (T1078); the decoy-session rule confirms it without a second alert".

### FB-10 · LOW · "BLOCK DISPATCHED — AWAITING SENSOR ACK" sometimes appears after the ACK
- **Where:** `OperationShells.run` (the banner is emitted after `work.apply` returns, racing the Sensor's ACK on the receive thread).
- **Actual:** in dry run 1 (and so in the published `live-op-shell-block.png`, Figure 11) and in `h1`, the order is `… confirms: BLOCK 127.0.0.1 applied` then `BLOCK DISPATCHED — AWAITING SENSOR ACK`. In `h2` it was in the right order 3 times out of 3. It's intermittent.
- **Evidence:** [`dry1/shots/live-op-shell-block.png`](evidence/dry1/shots/live-op-shell-block.png); `h1\run.log` "defence op shell banners (order check)"; `h2\run.log` R6.
- **Suggested fix:** emit the banner before broadcasting, or word it `BLOCK DISPATCHED` without "awaiting".

### FB-11 · LOW · The stealth slider doesn't slow the attempts
- **Where:** `Attacker.runCampaign` (the delay is only between strategies, and also after the last one); `launchConcurrent` ignores it.
- **Actual:** brute force with 1000 ms finished all 8 tries in about 200 ms (1,225 ms in total). Spray with 2000 ms finished its 4 tries in 307 ms, then waited 2 s before "complete". It only matters in Full campaign, sequential.
- **Evidence:** `h1/run.log` S20, `h2/run.log` R8.
- **Suggested fix:** pass the delay into the strategies (between attempts), or rename it to "Delay between techniques".

### FB-12 · LOW · Non-IP targets are refused as "OUTSIDE LAB RANGE" with an odd message
- **Where:** `SpecterConsole.onLaunch` / `ScopeGuard` message.
- **Actual:** `localhost`, an empty host and `not a host` all give `REFUSED — OUTSIDE LAB RANGE`; for an empty host the line reads `ScopeGuard refused :  is outside the authorised lab range`; the line is tagged `[AWT-EventQueue-0]`. Safe, but confusing (`localhost` *is* 127.0.0.1).
- **Evidence:** [`h1/shots/b-specter-empty-host.png`](evidence/h1/shots/b-specter-empty-host.png); `h1\run.log` S19 lines.
- **Suggested fix:** validate first and say "not an IP address (use 127.0.0.1)"; give the line a `specter-ui` tag.

### FB-13 · LOW · Thread strip and split-pane cosmetics
- **Actual:** finished operator threads stay `•run` for ever (`op-tarpit •run`, `op-block •run`, `op-counter •run` in popups and op shells); the strip clips labels instead of wrapping; split-pane titles are drawn dark grey on the near-black background (hard to read on a projector); the counter-watch's first line is tagged `[AWT-EventQueue-0]`.
- **Evidence:** [`dry1/shots/live-threat-popup-neutralised.png`](evidence/dry1/shots/live-threat-popup-neutralised.png), `live-op-shell-block.png`, `live-counterwatch-incoming.png`, `live-specter-concurrent.png`.
- **Suggested fix:** emit a final line or done-marker per op thread; light pane-title colour from `ShellTheme`; wrap the strip.

### FB-14 · LOW · Network map details
- **Actual:** (a) a source with activity but no alert yet (REPORT §5.1 "starts grey") appears only when some other alert triggers `rebuildMap` (`10.10.0.77` was absent, then showed as grey `watching` after an unrelated alert). (b) The map always shows a horizontal scrollbar, and with many sources the second row is cut off. (c) Nodes sort by severity then IP string, so `10.10.0.x` sources come before the demo's `127.0.0.1`.
- **Evidence:** `h2/run.log` R2; [`h2/shots/r-dashboard-1024x700-fresh.png`](evidence/h2/shots/r-dashboard-1024x700-fresh.png); [`h1/shots/b-dashboard-load.png`](evidence/h1/shots/b-dashboard-load.png).
- **Suggested fix:** rebuild the map on the 1 s detail timer too; make the map panel track the viewport width.

### FB-15 · LOW · "Ledger: verified" goes stale
- **Actual:** after a Verify, the status keeps `Ledger: verified` while 85 more alerts are appended (none of them checked).
- **Evidence:** `h1/run.log` (`Alerts: 89 … Ledger: verified`).
- **Suggested fix:** reset to `— (n new since check)` on each new alert, or re-verify on a timer.

### FB-16 · LOW · Many sources at once: popup storm, and the demo source's popup can be evicted
- **Actual:** 40 in-range sources (test sensor) → 12 popups, all visible and each pulled to the front; the 127.0.0.1 popup was LRU-evicted and disposed (my harness then hit a null popup, which is the only stack trace in any log). The UI stays usable: max EDT lag 1.46 s, the pipeline shell stays under its 5,000-line cap (3,618 shown of 10,045 produced). Not a demo scenario.
- **Evidence:** `h1/run.log` (`threat popups displayable=12 visible=12`, `max EDT lag … 1455 ms`, NPE at the end), [`h1/shots/b-dashboard-load.png`](evidence/h1/shots/b-dashboard-load.png).
- **Suggested fix:** don't `toFront()` more than one popup per second; never evict a popup for a source that is still being acted on.

### FB-17 · LOW · Alert feed columns truncated at the default window size
- **Actual:** at 1120×680 the Time column shows `2026-11-08T00:02:..` and Classification `Stolen credentials u…` (also visible in the published `live-dashboard.png`).
- **Evidence:** [`dry1/shots/live-dashboard.png`](evidence/dry1/shots/live-dashboard.png).
- **Suggested fix:** show time as `HH:mm:ss` and set preferred column widths.

### FB-18 · LOW · Doc wording mismatches
- DEMO step 0 status bar: documented `Console at … · Sensors connected: 1 · Auto-response: OFF (operator)`; actual `Console at 127.0.0.1:8443 | Alerts: 0 | Sensors connected: 1 | Auto-response: OFF (operator) | Ledger: —`.
- DEMO step 5 pane names `op-port-scan`, `op-mirror-…`: the split panes are `op-counter-all`, `op-port-scan`, `op-brute-force`, `op-password-spray`, `op-honeytoken-replay` (`op-mirror-…` belongs to the single counter, before the split).
- DEMO bait section: "the `bait/` folder next to the jar". With `run-defender.bat` it is created next to the **.bat** (the repo root), not next to `target\mirage.jar`.
- README project layout: `unit tests (98)` (265 elsewhere).
- HANDOVER "Left to do" item 3 says CLAUDE.md still reads "27 checks / JDK 21". CLAUDE.md at `b6d7f23` already says 28 checks and JDK 17, so that item is stale.
- LiveDemoShot output on a Windows console prints `?`/`�` for `→`/`—` (cp1252), e.g. `auto-response: Console blocks by itself ? NEUTRALISED`.
- **Evidence:** `h1/run.log`, `dry1/run.log`, `launcher2/` (repo root got `bait`, `data`; cleaned afterwards).

### FB-19 · LOW · Closing the counter-watch window leaves its listener running
- **Where:** `SpecterMain.startCounterWatch` (`DISPOSE_ON_CLOSE`, the `CounterWatch` isn't stopped).
- **Actual:** after WM_CLOSE on `Specter — counter-watch (incoming)` the attacker JVM still owns `:8080`, so later counters are accepted silently with nothing on screen. Closing the Specter window still exits the JVM cleanly.
- **Evidence:** `launcher2/` run: `port 8080 listener after closing counter-watch window: owner 6588 (attacker 6588)`.
- **Suggested fix:** stop the `CounterWatch` in a `windowClosed` listener, or make the window `DO_NOTHING_ON_CLOSE` + iconify.

## Not tested / limits
- Real two-laptop network, TLS between machines, firewall behaviour (A's / the rehearsal's job). FB-01 shows what Specter will display if the firewall drops packets.
- OS dark mode: Swing's Metal LAF ignores the Windows theme, so there is no dark/light switch to test; only the fixed theme was checked.
- "Bait change while the defender was closed is reported at next start": not tested (back-end, A's area).
- Window placement on the physical screen was not captured (windows were rendered to PNG with `printAll`, so no other desktop content was recorded).
