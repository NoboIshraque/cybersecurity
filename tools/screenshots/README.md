# GUI screenshot harnesses

Offline harnesses that render the real Swing GUIs and capture the figures used in
[`REPORT.md`](../../REPORT.md) Section 5 (and the Report artifact). They are **not** part of
the application — they build the real `Dashboard` / `SpecterConsole` components, drive them,
and grab a PNG. Everything stays inside the `ScopeGuard` lab range.

## Files

- `DashboardShot.java` — builds the Console `Dashboard`, seeds the `AttackerDirectory` with
  sample activity for four sources, publishes six alerts, selects a source, and renders one
  view. Arg: `dossier` (default) | `replay` | `responses` | `broken`. In `broken` mode it
  corrupts one stored ledger entry's hash so the integrity check reports BROKEN and the
  tampered row highlights red.
- `SpecterShot.java` — builds the `SpecterConsole`, selects the full campaign, and launches
  it against `127.0.0.1` (authorised by the lab range) so the live attack log fills.
- `capture.sh` — compiles both harnesses, runs each under a headless `Xvfb` display,
  screenshots with ImageMagick, trims/frames, and writes the four figures to
  `docs/screenshots/`: `console-dashboard.png` (Fig 5), `specter-console.png` (Fig 6),
  `detail-replay-responses.png` (Fig 7), `ledger-broken.png` (Fig 8).

## Regenerate the figures

```bash
mvn compile
tools/screenshots/capture.sh
```

Requires a JDK, `Xvfb` / `xvfb-run`, and ImageMagick (`import`, `convert`). The harnesses run
on a virtual display, so no desktop is needed.

## Live-shells dry run (`LiveDemoShot.java`)

`LiveDemoShot` is the Phase 8 dry run of the live-shells demo **and** the source of the
`docs/screenshots/live-*.png` figures. It starts the real Console, Sensor and Specter in one JVM
(default config, everything on `127.0.0.1`, counter-watch moved to `:18080` so a busy `:8080`
can't skew it) and drives them through their own buttons: every Specter campaign (one at a time
and all at once), Tarpit / Feed fake data / Block from the threat popup, Counter-attack and
Simultaneous counter landing on the counter-watch, Unblock from the dashboard, auto-response
switched on, and an out-of-range target being refused. It prints `PASS`/`FAIL` for 27 checks
and exits non-zero if any fail. Windows are rendered straight to PNG (no OS title bar), so other
windows on the desktop never end up in a figure. It needs a real display; it isn't headless.

Windows (PowerShell or Git Bash), from the repo root, after `.\mvnw.cmd compile`:

```bash
./mvnw -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt
mkdir -p /tmp/mirage-dry && CP="$PWD/target/classes;$(cat target/cp.txt)"   # use ':' on macOS/Linux
javac -encoding UTF-8 -cp "$CP" -d /tmp/mirage-dry/out tools/screenshots/LiveDemoShot.java
(cd /tmp/mirage-dry && java -cp "out;$CP" LiveDemoShot "$OLDPWD/docs/screenshots")
```

Run it from an empty folder as shown: it creates `data/` (H2) and `bait/` in the working directory.

| Figure | Shows |
| --- | --- |
| `live-specter-recon.png` | Specter recon shell → `SCAN COMPLETE — n OPEN` |
| `live-specter-access-granted.png` | honeytoken replay → `ACCESS GRANTED` (the decoy's fake admin page) |
| `live-specter-concurrent.png` | full campaign "all at once", one pane per thread |
| `live-threat-popup-compromised.png` | Console threat popup, `COMPROMISED`, waiting for the operator |
| `live-op-shell-block.png` | operation shell: operator → ScopeGuard → broadcast → Sensor ACK, per op thread |
| `live-threat-popup-neutralised.png` | the popup after the Sensor confirms the block |
| `live-specter-stalled.png` / `live-specter-blocked.png` | Specter after a tarpit / a block |
| `live-op-shell-counter-split.png` | simultaneous counter, split per counter thread |
| `live-counterwatch-incoming.png` | the attacker's counter-watch: `INCOMING COUNTER-ATTACK` |
| `live-pipeline-shell.png` | the whole Console pipeline, incl. the auto-response dispatch |
| `live-dashboard.png` | dashboard with the auto-response switch on and the status bar |
| `live-specter-refused.png` | an out-of-range target → `REFUSED — OUTSIDE LAB RANGE` |
