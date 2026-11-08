# Pre-rehearsal feature check — fix checklist

Branch `feature/live-shells` @ `b6d7f23`, tested 2026-11-08 on Windows 11 with JDK 25. This came
after the stress-test fixes were finished (`docs/stress-test/CHECKLIST.md`). Two agents ran in
parallel and drove the real running program:

- **A — headless, network and back-end** → [`A-backend.md`](A-backend.md) (40 features checked:
  38 PASS, 2 PASS with a note, 0 FAIL; 2 LOW findings).
- **B — GUI, operator and demo** → [`B-gui.md`](B-gui.md) (32 features checked: 23 PASS, 8 PARTIAL,
  0 FAIL, 1 not tested; 8 MEDIUM and 11 LOW findings). Evidence screenshots are in
  [`evidence/`](evidence/).

**Bottom line:** no HIGH findings. The safety boundary held everywhere. The test suite passed
265/265 on two runs, and the GUI dry run passed 28/28 on two runs. Nothing is flaky. TLS, the
ledger, the database, every detection rule, every response and reconnecting all work as the docs
describe. What's left is mostly about **what the screen tells the presenter and the audience**,
plus some doc wording.

This file is the work list. Tick an item when its fix is merged **with a test that proves it**
(a test that fails before the fix and passes after; for doc-only lines, the corrected doc). Each
line links to the full entry (where, steps, evidence, suggested fix). Items that overlap are
merged into one line.

## Wave 1 — before the two-laptop rehearsal

These are the things most likely to confuse or block the rehearsal itself.

- [x] **MEDIUM** · [FB-01](B-gui.md) — Specter shows **CONNECTION BLOCKED** when the connection only *failed* (closed port, Sensor down, unreachable host). For an unreachable host it also hangs for about 21 s first, because the probe has no connect timeout. On two laptops, a firewall problem would look like the defender blocking the attacker. *Separate banner (`CONNECTION FAILED — NOTHING LISTENING`) when `connectFailed()`; `socket.connect(addr, 3000)` in `ProbeReply`.*
- [x] **MEDIUM** · FC-01 (found while fixing FB-01) — Two tests are flaky when the machine is busy. On unchanged code (`d34f9dd`), the full suite failed in 3 of 5 runs. `CounterWatchFloodTest` got `Connection refused` on a burst of 60–100 quick connects; on Windows a full accept backlog (default 50) refuses connections instead of queueing them. `BoundedReadsOnTheWireTest.everyDecoyCutsOffALineWithNoEnd` failed once. Neither fails when run alone (0 of 15). *Give `CounterWatch` (and the decoys) a larger accept backlog; make the tests independent of how fast the accept thread is.*
- [x] **MEDIUM** · [FB-08](B-gui.md) — If start-up is refused, a double-clicked `run-*.bat` window closes at once, so the refusal message is lost. `attacker` and `sensor` refusals also exit with code 0. *`if errorlevel 1 pause` in both .bat files; `System.exit(1)` on a refused start in `SpecterMain` and `SensorMain`.*
- [x] **LOW** · [FB-19](B-gui.md) — Closing the counter-watch window leaves its listener running on :8080, so later counters are accepted with nothing on screen. *Stop the `CounterWatch` when the window closes (or don't let it close).*
- [ ] **LOW** · [FA-01](A-backend.md) — A force-killed defender leaves a stale H2 lock, so the next start in that folder refuses with `data\mirage is in use`. That message isn't in the troubleshooting table. *Add a troubleshooting row; optionally retry or clear a stale lock.*
- [ ] **MEDIUM** · [FB-05](B-gui.md) — The dry-run commands in `tools/screenshots/README.md` fail on Windows: in Git Bash, `$PWD` gives `/e/...` and javac reports 17 errors, and there is no PowerShell version. DEMO points presenters at this dry run as the rehearsal check. *Use `$(pwd -W)`; add a PowerShell block or a `dry-run.cmd`.*
- [ ] **MEDIUM** · [FB-04](B-gui.md) — Below about 1100 px wide the dashboard and popup lose controls: at 1024 px the alert feed collapses and the `Ledger:` value is cut off, at 800 px **Unblock** disappears, and at 640 px the popup loses **Inspect in dashboard**. A 1024×768 projector or a 125 % scaled laptop screen would hit this. *Wrapping button rows and labels, ledger state on its own label, size windows from the screen size.*

## Wave 2 — make the demo show what it claims

- [ ] **MEDIUM** · [FB-02](B-gui.md) — The ledger tamper demo can't show the red row. After a restart, the stored alerts aren't loaded into the feed (`Alerts: 0` while the ledger holds 3). Verify says `BROKEN (entry 1)` but no row turns red, and the status reads `Ledger: —` until Verify is clicked. *Seed the alert table from `ledger.entries()` at start-up and check the chain once at start; or reword DEMO step 7 and the `[warn]` line.*
- [ ] **MEDIUM** · [FB-03](B-gui.md) + [FB-07](B-gui.md) — In split view only 1 or 2 of the 4–5 thread panes are on screen (the rest need horizontal scrolling); Figure 13 shows this too. Turning on **Split threads** after lines have arrived gives an empty pane, and the counter shell loses the single counter's lines when Simultaneous counter switches it to split view. *Make the split container fit the viewport width (or use a 2×N grid); rebuild the panes from a bounded history kept per thread.*
- [ ] **MEDIUM** · [FB-06](B-gui.md) — DEMO step 4 promises a fake file manager showing `passwords.csv`, `vpn.key` and `backup.cfg`, but no GUI ever shows those files; Specter prints only the first reply line. *Have Specter list the file names from a 200 reply, or reword DEMO step 4.*
- [ ] **LOW** · [FB-10](B-gui.md) — `BLOCK DISPATCHED — AWAITING SENSOR ACK` sometimes appears *after* the Sensor's ACK. It's a race, and it shows in the published Figure 11. *Emit the banner before broadcasting, or drop "awaiting".*
- [ ] **LOW** · [FB-09](B-gui.md) + [FB-18](B-gui.md) — Doc wording that no longer matches the program:
  - DEMO step 3 and REPORT Figure 10 still promise a second "Intruder inside decoy" CRITICAL alert.
  - The DEMO status-bar text differs from the real one.
  - The DEMO step 5 pane names don't match the real panes.
  - DEMO says the `bait/` folder is next to the jar, but with `run-defender.bat` it is next to the .bat.
  - The README still says "unit tests (98)".
  - HANDOVER "Left to do" item 3 about CLAUDE.md is stale (CLAUDE.md already says 28 checks and JDK 17).

  *Doc edits; regenerate Figures 10, 11 and 13 after the Wave 2 code fixes.*

## Wave 3 — polish (fix if time allows)

- [ ] **LOW** · [FB-11](B-gui.md) — The stealth slider only adds a delay between techniques, not between attempts, and "All at once" ignores it. *Pass the delay into the strategies, or rename it "Delay between techniques".*
- [ ] **LOW** · [FB-12](B-gui.md) — `localhost`, an empty host and other non-IP targets are refused as "OUTSIDE LAB RANGE" (an empty host reads `ScopeGuard refused :  is outside…`). *Check for a valid IP first and say "not an IP address (use 127.0.0.1)".*
- [ ] **LOW** · [FB-13](B-gui.md) — Shell cosmetics: finished operator threads stay `•run`, the thread strip cuts labels off, split-pane titles are dark grey on black, and some lines are tagged `AWT-EventQueue-0`.
- [ ] **LOW** · [FB-14](B-gui.md) — Network map: a source with activity but no alert only appears after some other alert, there is always a horizontal scrollbar, and with many sources the second row is cut off.
- [ ] **LOW** · [FB-15](B-gui.md) — `Ledger: verified` stays on the status line after new, unchecked alerts arrive.
- [ ] **LOW** · [FB-17](B-gui.md) — The Time and Classification columns in the alert feed are cut off at the default window size. *Show the time as `HH:mm:ss`; set preferred column widths.*
- [ ] **LOW** · [FB-16](B-gui.md) — With 40 sources at once, 12 popups all jump to the front and the demo source's popup can be closed to make room. This isn't a demo scenario, and the UI stays usable. *Limit `toFront()`; never close the popup of a source that is being acted on.*
- [ ] **LOW** · [FA-02](A-backend.md) — On JDK 25, `mvnw package` prints native-access / `sun.misc.Unsafe` warnings. They come from Maven's own runtime, not Mirage. *No code fix; build the jar before the demo.*

## Not covered by this check

- The real two-laptop LAN, TLS between two machines, and firewall behaviour: that's what the rehearsal is for. FB-01 shows what Specter will display if the firewall drops packets.
- IPv6 and a dependency CVE scan (same as the stress test).
- OS dark mode: Swing's Metal look-and-feel ignores the Windows theme.
- On Windows every `127.x` address shows as source `127.0.0.1`, so A tested multi-source logic with harnesses that use synthetic IPs instead of live loopback.
- Agent A's and B's own click-through harnesses and full logs were left in the session's scratch folder and are not in the repo. The reports quote the relevant lines, and the screenshots are in `evidence/`.
